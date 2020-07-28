package com.example.aoastream;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;

import android.util.Log;

public class AccessoryEngine {
    public static final String TAG = "AccessoryEngine";
    private static final int BUFFER_SIZE = 1024*1024;
    private final Context mContext;
    private final UsbManager mUsbManager;
    private final IEngineCallback mCallback;

    private final static String ACTION_ACCESSORY_DETACHED = "android.hardware.usb.action.USB_ACCESSORY_DETACHED";
    private static final String ACTION_USB_PERMISSION = "com.example.adrian.test_aoa.USB_PERMISSION";
    private volatile boolean mAccessoryConnected = false;
    private final AtomicBoolean mQuit = new AtomicBoolean(false);

    private UsbAccessory mAccessory = null;

    private ParcelFileDescriptor mParcelFileDescriptor = null;
    private FileDescriptor mFileDescriptor = null;
    private FileInputStream mInputStream = null;
    private FileOutputStream mOutputStream = null;

    public interface IEngineCallback {
        void onConnectionEstablished();

        void onDeviceDisconnected();

        void onConnectionClosed();

        void onDataRecieved(byte[] data, int num);
    }

    public AccessoryEngine(Context applicationContext, IEngineCallback callback) {
        mContext = applicationContext;
        mCallback = callback;
        mUsbManager = (UsbManager) mContext
                .getSystemService(Context.USB_SERVICE);
        mContext.registerReceiver(mDetachedReceiver, new IntentFilter(
                ACTION_ACCESSORY_DETACHED));
    }

    public void onNewIntent(Intent intent) {
        if (mUsbManager.getAccessoryList() != null) {
            mAccessory = mUsbManager.getAccessoryList()[0];
            connect();
        }
    }

    private void connect() {
        if (mAccessory != null) {
            if(!mUsbManager.hasPermission(mAccessory)){
                mContext.registerReceiver(mPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
                PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
                mUsbManager.requestPermission(mAccessory, pi);
                return;
            }
            if (sAccessoryThread == null) {
                sAccessoryThread = new Thread(mAccessoryRunnable,
                        "Reader Thread");
                sAccessoryThread.start();
            } else {
                Log.d(TAG, "reader thread already started");
            }
        } else {
            Log.d(TAG, "accessory is null");
        }
    }

    public void onDestroy() {
        // closeConnection();
        mQuit.set(true);
        mContext.unregisterReceiver(mDetachedReceiver);
    }

    private final BroadcastReceiver mDetachedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null
                    && ACTION_ACCESSORY_DETACHED.equals(intent.getAction())) {
                mCallback.onDeviceDisconnected();
            }
        }
    };

    private final BroadcastReceiver mPermissionReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext.unregisterReceiver(mPermissionReceiver);
            if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)){
                connect();
            }
        }
    };

    public void write(byte[] data) {
        if (mAccessoryConnected && mOutputStream != null) {
            try {
                mOutputStream.write(data);
            } catch (IOException e) {
                Log.e(TAG, "could not send data");
            }
        } else {
            Log.d(TAG, "accessory not connected");
        }
    }

    private static Thread sAccessoryThread;
    private final Runnable mAccessoryRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "open connection");
            mParcelFileDescriptor = mUsbManager.openAccessory(mAccessory);
            if (mParcelFileDescriptor == null) {
                Log.e(TAG, "could not open accessory");
                mCallback.onConnectionClosed();
                return;
            }
            mFileDescriptor = mParcelFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(mFileDescriptor);
            mOutputStream = new FileOutputStream(mFileDescriptor);
            mCallback.onConnectionEstablished();
            mAccessoryConnected = true;

            byte[] buf = new byte[BUFFER_SIZE];
            while (!mQuit.get()) {
                try {
                    int read = mInputStream.read(buf);
                    mCallback.onDataRecieved(buf, read);
                    write("hello!".getBytes());
                } catch (Exception e) {
                    Log.e(TAG, "ex " + e.getMessage());
                    break;
                }
            }
            Log.d(TAG, "exiting reader thread");
            mCallback.onConnectionClosed();

            if (mParcelFileDescriptor != null) {
                try {
                    mParcelFileDescriptor.close();
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close ParcelFD");
                }
            }

            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close InputStream");
                }
            }

            if (mOutputStream != null) {
                try {
                    mOutputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close OutputStream");
                }
            }

            mAccessoryConnected = false;
            mQuit.set(false);
            sAccessoryThread = null;
        }
    };
}
