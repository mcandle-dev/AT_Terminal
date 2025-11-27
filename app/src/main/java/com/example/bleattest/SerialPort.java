package com.example.bleattest;

import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Serial Port helper class using JNI for native /dev/tty* access
 */
public class SerialPort {
    private static final String TAG = "SerialPort";

    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    /**
     * Open serial port
     * @param device Device path (e.g. "/dev/ttyS0", "/dev/ttyUSB0")
     * @param baudrate Baud rate (e.g. 115200)
     * @param flags Additional flags (0 for default)
     * @throws SecurityException if permission denied
     * @throws IOException if device open failed
     */
    public SerialPort(File device, int baudrate, int flags) throws SecurityException, IOException {
        // Check access permission
        if (!device.canRead() || !device.canWrite()) {
            try {
                // Try to set permission using su
                Process su = Runtime.getRuntime().exec("/system/bin/su");
                String command = "chmod 666 " + device.getAbsolutePath() + "\n" + "exit\n";
                su.getOutputStream().write(command.getBytes());
                if (su.waitFor() != 0 || !device.canRead() || !device.canWrite()) {
                    throw new SecurityException("No read/write permission for " + device.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set permission", e);
                throw new SecurityException("No read/write permission for " + device.getAbsolutePath());
            }
        }

        mFd = open(device.getAbsolutePath(), baudrate, flags);
        if (mFd == null) {
            Log.e(TAG, "Native open() returned null");
            throw new IOException("Failed to open serial port");
        }

        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }

    /**
     * Get input stream
     */
    public FileInputStream getInputStream() {
        return mFileInputStream;
    }

    /**
     * Get output stream
     */
    public FileOutputStream getOutputStream() {
        return mFileOutputStream;
    }

    /**
     * Close serial port
     */
    public void close() {
        try {
            if (mFileInputStream != null) {
                mFileInputStream.close();
                mFileInputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close input stream", e);
        }

        try {
            if (mFileOutputStream != null) {
                mFileOutputStream.close();
                mFileOutputStream = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close output stream", e);
        }

        if (mFd != null) {
            close(mFd);
            mFd = null;
        }
    }

    /**
     * Native method to open serial port
     * @param path Device path
     * @param baudrate Baud rate
     * @param flags Additional flags
     * @return FileDescriptor or null
     */
    private native static FileDescriptor open(String path, int baudrate, int flags);

    /**
     * Native method to close serial port
     * @param fd FileDescriptor
     */
    private native void close(FileDescriptor fd);

    static {
        System.loadLibrary("serial_port");
    }
}
