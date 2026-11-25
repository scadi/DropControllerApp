package com.example.dropcontroller.logic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BleTransportLogicTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    // ----- BleChunker -----

    @Test
    public void chunker_shortMessage_isOnePacket() {
        List<byte[]> packets = BleChunker.split(bytes("[AYT]"));

        assertEquals(1, packets.size());
        assertArrayEquals(bytes("[AYT]"), packets.get(0));
    }

    @Test
    public void chunker_exactlyOnePacket_isNotSplit() {
        byte[] data = new byte[BleChunker.MAX_PACKET_SIZE];

        assertEquals(1, BleChunker.split(data).size());
    }

    @Test
    public void chunker_longMessage_keepsOrderAndSizeLimit() throws IOException {
        byte[] data = bytes("[V,dC3_AS25006,Nov 17 2026] and more text to pass 40 bytes");

        List<byte[]> packets = BleChunker.split(data);

        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] packet : packets) {
            assertTrue(packet.length <= BleChunker.MAX_PACKET_SIZE);
            joined.write(packet);
        }
        assertEquals(3, packets.size());
        assertArrayEquals(data, joined.toByteArray());
    }

    @Test
    public void chunker_emptyData_hasNoPackets() {
        assertTrue(BleChunker.split(new byte[0]).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void chunker_zeroSize_isRejected() {
        BleChunker.split(bytes("x"), 0);
    }

    // ----- ByteQueueInputStream -----

    @Test
    public void queue_returnsOfferedBytesInOrder() throws IOException {
        ByteQueueInputStream in = new ByteQueueInputStream();
        in.offer(bytes("[YI"));
        in.offer(bytes("A]"));
        byte[] buffer = new byte[16];

        int count = in.read(buffer);

        assertEquals(5, count);
        assertEquals("[YIA]", new String(buffer, 0, count, StandardCharsets.US_ASCII));
    }

    @Test
    public void queue_smallBuffer_readsAChunkInParts() throws IOException {
        ByteQueueInputStream in = new ByteQueueInputStream();
        in.offer(bytes("ABCDE"));
        byte[] buffer = new byte[2];

        assertEquals(2, in.read(buffer));
        assertEquals(3, in.available());
        assertEquals(2, in.read(buffer));
        assertEquals(1, in.read(buffer));
        assertEquals('E', buffer[0]);
    }

    @Test
    public void queue_closeAfterData_deliversDataThenEnd() throws IOException {
        ByteQueueInputStream in = new ByteQueueInputStream();
        in.offer(bytes("AB"));
        in.close();
        byte[] buffer = new byte[8];

        assertEquals(2, in.read(buffer));
        assertEquals(-1, in.read(buffer));
    }

    @Test
    public void queue_failAfterData_deliversDataThenThrows() throws IOException {
        ByteQueueInputStream in = new ByteQueueInputStream();
        in.offer(bytes("AB"));
        in.fail(new IOException("link lost"));
        byte[] buffer = new byte[8];

        assertEquals(2, in.read(buffer));
        try {
            in.read(buffer);
            fail("expected IOException");
        } catch (IOException e) {
            assertEquals("link lost", e.getMessage());
        }
    }

    @Test
    public void queue_offerAfterClose_isIgnored() throws IOException {
        ByteQueueInputStream in = new ByteQueueInputStream();
        in.close();
        in.offer(bytes("late"));

        assertEquals(-1, in.read(new byte[4]));
    }

    @Test(timeout = 5000)
    public void queue_blockedReader_wakesUpOnOffer() throws Exception {
        ByteQueueInputStream in = new ByteQueueInputStream();
        byte[] buffer = new byte[8];
        int[] count = {0};
        Thread reader = new Thread(() -> {
            try {
                count[0] = in.read(buffer);
            } catch (IOException e) {
                count[0] = -2;
            }
        });
        reader.start();
        Thread.sleep(50);

        in.offer(bytes("ok"));
        reader.join();

        assertEquals(2, count[0]);
    }

    @Test(timeout = 5000)
    public void queue_blockedReader_wakesUpOnClose() throws Exception {
        ByteQueueInputStream in = new ByteQueueInputStream();
        int[] count = {0};
        Thread reader = new Thread(() -> {
            try {
                count[0] = in.read(new byte[8]);
            } catch (IOException e) {
                count[0] = -2;
            }
        });
        reader.start();
        Thread.sleep(50);

        in.close();
        reader.join();

        assertEquals(-1, count[0]);
    }

    // ----- Round trip: device replies in 20-byte notifications, the framer joins them -----

    @Test
    public void roundTrip_longVersionReply_arrivesInPacketsAndIsJoined() throws IOException {
        DeviceSimulator simulator = new DeviceSimulator("dC3_AS25006", "Nov 17 2026");
        ByteQueueInputStream in = new ByteQueueInputStream();
        MessageFramer framer = new MessageFramer();

        // The app writes [VERSION]; the device answers, the module sends the answer in 20-byte notifications
        DeviceSimulator.Result result = simulator.feed("[VERSION]");
        for (String reply : result.getMessages()) {
            for (byte[] packet : BleChunker.split(bytes(reply))) {
                in.offer(packet);
            }
        }
        in.close();

        List<String> payloads = new ArrayList<>();
        byte[] buffer = new byte[256];
        int count;
        while ((count = in.read(buffer)) > 0) {
            payloads.addAll(framer.feed(buffer, 0, count));
        }

        assertEquals(1, payloads.size());
        assertEquals("V,dC3_AS25006,Nov 17 2026", payloads.get(0));
    }

    @Test
    public void roundTrip_appMessagesSplitIntoPackets_areUnderstoodByTheDevice() {
        DeviceSimulator simulator = new DeviceSimulator();
        String message = "[D011222233333]";
        List<String> replies = new ArrayList<>();

        // Even a message cut at an arbitrary place must reach the device whole
        for (byte[] packet : BleChunker.split(bytes("[AYT]" + message), 7)) {
            replies.addAll(simulator.feed(packet, 0, packet.length).getMessages());
        }

        assertEquals(1, replies.size());
        assertEquals("[YIA]", replies.get(0));
    }

    // ----- BluetoothTransport -----

    @Test
    public void transport_pairedLeOnlyDevice_usesBle() {
        assertEquals(BluetoothTransport.BLE, BluetoothTransport.forPairedDevice(2));
    }

    @Test
    public void transport_classicDualAndUnknown_useRfcomm() {
        assertEquals(BluetoothTransport.RFCOMM, BluetoothTransport.forPairedDevice(1));
        assertEquals(BluetoothTransport.RFCOMM, BluetoothTransport.forPairedDevice(3));
        assertEquals(BluetoothTransport.RFCOMM, BluetoothTransport.forPairedDevice(0));
    }

    @Test
    public void transportKey_rfcommKeepsTheOldFormat() {
        String key = BluetoothTransport.key(BluetoothTransport.RFCOMM, "00:11:22:33:44:55");

        assertEquals("00:11:22:33:44:55", key);
        assertEquals(BluetoothTransport.RFCOMM, BluetoothTransport.transportOf(key));
    }

    @Test
    public void transportKey_bleRoundTrips() {
        String key = BluetoothTransport.key(BluetoothTransport.BLE, "00:15:87:00:94:4A");

        assertEquals("ble:00:15:87:00:94:4A", key);
        assertEquals(BluetoothTransport.BLE, BluetoothTransport.transportOf(key));
    }

    @Test
    public void transportOf_nullOrSimulatorKey_isRfcomm() {
        assertEquals(BluetoothTransport.RFCOMM, BluetoothTransport.transportOf(null));
        assertEquals(BluetoothTransport.RFCOMM, BluetoothTransport.transportOf("simulator"));
    }
}
