package com.example.testme.audiotest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.Manifest;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;

public class HearingService extends Service {

    private static final String TAG = "HearingService";
    public static final String ACTION_START = "com.example.testme.audiotest.ACTION_START";
    public static final String ACTION_STOP = "com.example.testme.audiotest.ACTION_STOP";
    private static final String ACTION_NOTIFICATION_DISMISSED = "com.example.testme.audiotest.ACTION_NOTIFICATION_DISMISSED";
    private static final String CHANNEL_ID = "hearing_aid_service_channel";
    private static final int NOTIFICATION_ID = 101;

    private volatile boolean isRunning = false;
    private volatile boolean stopRequested = false;
    private boolean bluetoothReceiverRegistered = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerBluetoothReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_NOTIFICATION_DISMISSED.equals(action)) {
            if (isRunning && !isBluetoothAudioDeviceConnected(this)) {
                stopAudioProcessing();
            } else {
                updateForegroundNotification(isRunning);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopAudioProcessing();
        } else if (!isBluetoothAudioDeviceConnected(this)) {
            if (ACTION_START.equals(action)) {
                Toast.makeText(this, "Connect your Bluetooth headset to start hearing.", Toast.LENGTH_SHORT).show();
            }
            stopAudioProcessing();
            stopSelf();
        } else {
            startAudioProcessing();
        }

        return START_STICKY;
    }

    private synchronized void startAudioProcessing() {
        if (isRunning) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start audio processing without RECORD_AUDIO permission");
            updateForegroundNotification(false);
            return;
        }

        // HEADSET and A2DP can both report CONNECTED; block duplicate start intents immediately.
        isRunning = true;
        stopRequested = false;
        updateForegroundNotification(true);

        new Thread(() -> {
            try {
                if (Config.hearingAudio == null || !Config.hearingAudio.isStart()) {
                    Config.hearingAudio = new HearingAudio(getApplicationContext());
                    Config.hearingAudio.start();
                }

                if (stopRequested) {
                    closeAudioEngine();
                    isRunning = false;
                    updateForegroundNotification(false);
                    return;
                }
                if (Config.fragmentPlaying != null) {
                    Config.fragmentPlaying.start();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting audio processing thread: ", e);
                isRunning = false;
                if (Config.fragmentPlaying != null) {
                    Config.fragmentPlaying.stop();
                }
                updateForegroundNotification(false);
            }
        }).start();
    }

    private synchronized void stopAudioProcessing() {
        stopRequested = true;
        closeAudioEngine();
        isRunning = false;
        if (Config.fragmentPlaying != null) {
            Config.fragmentPlaying.stop();
        }
        updateForegroundNotification(false);
    }

    private void closeAudioEngine() {
        try {
            if (Config.hearingAudio != null) {
                Config.hearingAudio.setLineBarVisualizer(null);
                Config.hearingAudio.close();
                Config.hearingAudio = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio engine: ", e);
        }
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this, bluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        bluetoothReceiverRegistered = true;
    }

    public static boolean isBluetoothAudioDeviceConnected(Context context) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                == BluetoothProfile.STATE_CONNECTED;
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                    && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    == BluetoothAdapter.STATE_OFF) {
                stopAudioProcessing();
                return;
            }

            if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                    || BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                if (state == BluetoothProfile.STATE_CONNECTED
                        && isBluetoothAudioDeviceConnected(HearingService.this)) {
                    startAudioProcessing();
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isBluetoothAudioDeviceConnected(HearingService.this)) stopAudioProcessing();
                    }, 500);
                }
            }
        }
    };

    private void updateForegroundNotification(boolean active) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent toggleIntent = new Intent(this, HearingService.class);
        toggleIntent.setAction(active ? ACTION_STOP : ACTION_START);
        PendingIntent togglePendingIntent = PendingIntent.getService(
                this, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissedIntent = new Intent(this, HearingService.class);
        dismissedIntent.setAction(ACTION_NOTIFICATION_DISMISSED);
        PendingIntent dismissedPendingIntent = PendingIntent.getService(
                this, 2, dismissedIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(active ? "Hearing Aid Active" : "Hearing Aid Paused")
                .setContentText(active ? "Processing audio in background..." : "Audio processing is paused")
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setContentIntent(contentPendingIntent)
                .setDeleteIntent(dismissedPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        builder.addAction(
                active ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                active ? "Stop" : "Start",
                togglePendingIntent);

        Notification notification = builder.build();

        if (active) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Hearing Aid Processing Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (isRunning) {
            updateForegroundNotification(true);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopAudioProcessing();
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
            bluetoothReceiverRegistered = false;
        }
        super.onDestroy();
    }

}
