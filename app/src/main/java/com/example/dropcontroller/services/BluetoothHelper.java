package com.example.dropcontroller.services;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

// This UUID is standard for Bluetooth SPP (Serial Port Profile), commonly used by Arduino/HC-05 modules
public class BluetoothHelper {

    private static final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private Handler handler; // Handler to post messages back to the main UI thread

    public BluetoothHelper(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.handler = new Handler(Looper.getMainLooper());
    }

    // ----------------------------------------------------
    // 1. Connection Discovery (Analogous to ListPicker.Elements)
    // ----------------------------------------------------

    // @SuppressLint("MissingPermission") is necessary because checking the permission status
    // is complicated and done in MainActivity, but the method requires the annotation.
    @SuppressLint("MissingPermission")
    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            handler.post(() -> Toast.makeText(context, "Bluetooth is not enabled.", Toast.LENGTH_SHORT).show());
            return null;
        }
        return bluetoothAdapter.getBondedDevices();
    }

    // ----------------------------------------------------
    // 2. Connection Logic (Analogous to BluetoothClient1.Connect)
    // ----------------------------------------------------

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Connect must be run on a separate thread (ConnectThread)
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    // Disconnect method
    public void disconnect() {
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        handler.post(() -> Toast.makeText(context, "Disconnected.", Toast.LENGTH_SHORT).show());
    }

    // ----------------------------------------------------
    // 3. Data Transfer (Analogous to BluetoothClient1.SendText)
    // ----------------------------------------------------

    public void write(String message) {
        // Create a copy of the connectedThread
        ConnectedThread r;
        synchronized (this) {
            if (connectedThread == null) {
                handler.post(() -> Toast.makeText(context, "Not connected to a device.", Toast.LENGTH_SHORT).show());
                return;
            }
            r = connectedThread;
        }
        r.write(message.getBytes());
    }

    // ----------------------------------------------------
    // Inner Thread Classes for Bluetooth Operations
    // ----------------------------------------------------

    // Thread for initiating an outgoing connection
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID_SECURE is the SPP UUID.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
            } catch (IOException e) {
                handler.post(() -> Toast.makeText(context, "Socket creation failed.", Toast.LENGTH_SHORT).show());
            }
            mmSocket = tmp;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and exit
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    // Could not close the client socket
                }
                connectionFailed();
                return;
            }

            // Connection succeeded; start the thread to handle data transfer
            connected(mmSocket);
        }

        // Closes the client socket and causes the thread to finish
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                // Ignore failure during close
            }
        }
    }

    // Thread for handling incoming and outgoing data
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                // Failed to get streams
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024]; // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // TODO: Send the obtained bytes to a handler in the Fragment/Activity
                    // for UI update (Analogous to when BluetoothClient1.BytesReceived)
                    String receivedData = new String(buffer, 0, bytes);
                    handler.post(() -> Toast.makeText(context, "Received: " + receivedData, Toast.LENGTH_SHORT).show());

                } catch (IOException e) {
                    connectionLost();
                    break;
                }
            }
        }

        // Call this from the MainActivity to send data to the remote device
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                handler.post(() -> Toast.makeText(context, "Error sending data.", Toast.LENGTH_SHORT).show());
            }
        }

        // Call this from the MainActivity to shutdown the connection
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                // Ignore failure during close
            }
        }
    }

    // Helper methods for status reporting
    private synchronized void connected(BluetoothSocket socket) {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the connected thread
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        handler.post(() -> Toast.makeText(context, "Successfully connected!", Toast.LENGTH_SHORT).show());
    }

    private void connectionFailed() {
        handler.post(() -> Toast.makeText(context, "Connection failed.", Toast.LENGTH_SHORT).show());
    }

    private void connectionLost() {
        handler.post(() -> Toast.makeText(context, "Connection lost.", Toast.LENGTH_SHORT).show());
        this.disconnect();
    }
}