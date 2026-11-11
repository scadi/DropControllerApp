package com.example.dropcontroller.services;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A byte stream to the dropController. The real connection uses an RFCOMM socket; tests and the debug simulator
 * use an in-process pipe, so {@link BluetoothHelper} does not need to know the difference.
 */
interface DeviceLink {

    @NonNull
    InputStream getInputStream() throws IOException;

    @NonNull
    OutputStream getOutputStream() throws IOException;

    void close();
}
