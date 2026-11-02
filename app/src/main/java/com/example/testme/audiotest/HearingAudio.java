
package com.example.testme.audiotest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.chibde.visualizer.LineBarVisualizer;

public class HearingAudio extends Thread
        implements Visualizer.OnDataCaptureListener {

    private static final String TAG = "HearingAudio";

    private static final int SAMPLE_RATE = 44100;
    private static final int ROUTE_CHECK_INTERVAL = 100;
    private static final long ROUTE_TIMEOUT = 5000;

    private volatile boolean stopped = false;
    private volatile boolean started = false;
    private volatile boolean outputMuted = true;

    private final Context context;
    private final int sampleRateInHz;

    private int recordBufferSize;
    private int trackBufferSize;

    private AudioManager audioManager;
    private Handler audioHandler;

    private AudioRecord recorder;
    private AudioTrack track;

    private LoudnessEnhancer enhancer;
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler acousticEchoCanceler;
    private Equalizer equalizer;
    private Visualizer visualizer;

    private LineBarVisualizer linebarVisualizer;

    private AudioDeviceCallback deviceCallback;
    private AudioTrack.OnRoutingChangedListener routingListener;

    private long routeCheckStarted;

    public HearingAudio(Context context) {
        super("HearingAudioThread");

        this.context = context.getApplicationContext();
        this.sampleRateInHz = SAMPLE_RATE;

        audioManager = (AudioManager)
                this.context.getSystemService(Context.AUDIO_SERVICE);

        audioHandler = new Handler(Looper.getMainLooper());

        init();
    }

    private void init() {

        Log.i(TAG, "Initializing audio engine");

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "RECORD_AUDIO permission not granted");
            return;
        }

        int encoding = AudioFormat.ENCODING_PCM_16BIT;

        int channelIn = AudioFormat.CHANNEL_IN_STEREO;
        int channelOut = AudioFormat.CHANNEL_OUT_STEREO;

        recordBufferSize = AudioRecord.getMinBufferSize(
                sampleRateInHz,
                channelIn,
                encoding
        );

        trackBufferSize = AudioTrack.getMinBufferSize(
                sampleRateInHz,
                channelOut,
                encoding
        );

        if (recordBufferSize <= 0 || trackBufferSize <= 0) {
            Log.e(TAG, "Unsupported audio configuration. Record: "
                    + recordBufferSize + ", Track: " + trackBufferSize);
            return;
        }

        try {

            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRateInHz,
                    channelIn,
                    encoding,
                    recordBufferSize * 2
            );

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                recorder.release();
                recorder = null;
                return;
            }

            int outputBufferSize = Math.max(
                    trackBufferSize * 2,
                    recordBufferSize
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                track = new AudioTrack.Builder()
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(
                                                AudioAttributes.CONTENT_TYPE_SPEECH
                                        )
                                        .build()
                        )
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setEncoding(encoding)
                                        .setSampleRate(sampleRateInHz)
                                        .setChannelMask(channelOut)
                                        .build()
                        )
                        .setBufferSizeInBytes(outputBufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

            } else {

                track = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRateInHz,
                        channelOut,
                        encoding,
                        outputBufferSize,
                        AudioTrack.MODE_STREAM
                );
            }

            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed");
                track.release();
                track = null;
                releaseRecorder();
                return;
            }

            // Always start with output muted.
            track.setVolume(0.0f);

            setupAudioEffects();

            Log.i(TAG, "Audio engine initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Audio initialization error", e);
            releaseResources();
        }
    }

    private void setupAudioEffects() {

        if (track == null || recorder == null) {
            return;
        }

        int trackSessionId = track.getAudioSessionId();
        int recordSessionId = recorder.getAudioSessionId();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                enhancer = new LoudnessEnhancer(trackSessionId);
                enhancer.setEnabled(false);
            } catch (Exception e) {
                Log.e(TAG, "LoudnessEnhancer initialization failed", e);
            }
        }

        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(recordSessionId);
            } catch (Exception e) {
                Log.e(TAG, "NoiseSuppressor initialization failed", e);
            }
        }

        if (AcousticEchoCanceler.isAvailable()) {
            try {
                acousticEchoCanceler =
                        AcousticEchoCanceler.create(recordSessionId);

                if (acousticEchoCanceler != null) {
                    acousticEchoCanceler.setEnabled(false);
                }

            } catch (Exception e) {
                Log.e(TAG, "AcousticEchoCanceler initialization failed", e);
            }
        }

        try {
            equalizer = new Equalizer(0, trackSessionId);
        } catch (Exception e) {
            Log.e(TAG, "Equalizer initialization failed", e);
        }

        try {
            visualizer = new Visualizer(trackSessionId);

            visualizer.setDataCaptureListener(
                    this,
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false
            );

            visualizer.setEnabled(true);

        } catch (Exception e) {
            Log.e(TAG, "Visualizer initialization failed", e);
        }
    }

    // ---------------------------------------------------------
    // BLUETOOTH A2DP ROUTING
    // ---------------------------------------------------------

    private AudioDeviceInfo findA2dpDevice() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                audioManager == null) {
            return null;
        }

        AudioDeviceInfo[] devices = audioManager.getDevices(
                AudioManager.GET_DEVICES_OUTPUTS
        );

        for (AudioDeviceInfo device : devices) {

            if (device.getType() ==
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {

                return device;
            }
        }

        return null;
    }

    private boolean isA2dpRouted() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                track == null) {
            return false;
        }

        try {

            AudioDeviceInfo routedDevice = track.getRoutedDevice();

            return routedDevice != null &&
                    routedDevice.getType() ==
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;

        } catch (Exception e) {
            Log.e(TAG, "Unable to verify audio route", e);
            return false;
        }
    }

    private void muteOutput() {

        outputMuted = true;

        AudioTrack currentTrack = track;

        if (currentTrack != null &&
                currentTrack.getState() == AudioTrack.STATE_INITIALIZED) {

            try {
                currentTrack.setVolume(0.0f);
            } catch (Exception e) {
                Log.e(TAG, "Unable to mute output", e);
            }
        }

        Log.w(TAG, "Audio output MUTED");
    }

    private void unmuteOutput() {

        if (stopped || !isA2dpRouted()) {
            return;
        }

        AudioTrack currentTrack = track;

        if (currentTrack == null ||
                currentTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }

        try {

            currentTrack.setVolume(1.0f);
            outputMuted = false;

            Log.i(TAG, "Bluetooth A2DP output ACTIVE");

        } catch (Exception e) {
            Log.e(TAG, "Unable to restore output", e);
            muteOutput();
        }
    }

    private void requestBluetoothRoute() {

        muteOutput();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                track == null || stopped || !started ||
                track.getState() != AudioTrack.STATE_INITIALIZED) {

            Log.e(TAG, "Bluetooth routing requires Android 6.0 or newer");
            return;
        }

        AudioDeviceInfo device = findA2dpDevice();

        if (device == null) {
            Log.w(TAG, "No A2DP device available");
            return;
        }

        try {

            boolean accepted = track.setPreferredDevice(device);

            Log.i(TAG, "A2DP preferred route accepted: " + accepted);

            if (!accepted) {
                Log.e(TAG, "Unable to request A2DP route");
                return;
            }

            routeCheckStarted = SystemClock.elapsedRealtime();

            audioHandler.removeCallbacks(routeCheckRunnable);
            audioHandler.post(routeCheckRunnable);

        } catch (Exception e) {
            Log.e(TAG, "Bluetooth route request failed", e);
        }
    }

    private final Runnable routeCheckRunnable = new Runnable() {

        @Override
        public void run() {

            if (stopped || track == null) {
                return;
            }

            if (isA2dpRouted()) {
                unmuteOutput();
                return;
            }

            long elapsed = SystemClock.elapsedRealtime()
                    - routeCheckStarted;

            if (elapsed >= ROUTE_TIMEOUT) {

                Log.e(TAG,
                        "A2DP routing timeout. Output remains muted.");

                return;
            }

            audioHandler.postDelayed(
                    this,
                    ROUTE_CHECK_INTERVAL
            );
        }
    };

    private void registerBluetoothListeners() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                audioManager == null || track == null ||
                deviceCallback != null || routingListener != null) {
            return;
        }

        deviceCallback = new AudioDeviceCallback() {

            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] devices) {
                super.onAudioDevicesAdded(devices);

                if (findA2dpDevice() != null) {

                    Log.i(TAG, "A2DP device connected");

                    requestBluetoothRoute();
                }
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) {
                super.onAudioDevicesRemoved(devices);

                for (AudioDeviceInfo device : devices) {

                    if (device.getType() ==
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {

                        if (findA2dpDevice() != null) {
                            // Another A2DP output is still available; request it
                            // instead of leaving the track pinned to the removed one.
                            requestBluetoothRoute();
                        } else {
                            Log.w(TAG, "A2DP device disconnected");

                            muteOutput();

                            audioHandler.removeCallbacks(
                                    routeCheckRunnable
                            );

                            try {
                                track.setPreferredDevice(null);
                            } catch (Exception e) {
                                Log.e(TAG, "Unable to clear A2DP route", e);
                            }
                        }

                        return;
                    }
                }
            }
        };

        audioManager.registerAudioDeviceCallback(
                deviceCallback,
                audioHandler
        );

        routingListener = changedTrack -> {

            if (stopped) {
                return;
            }

            if (isA2dpRouted()) {

                Log.i(TAG, "AudioTrack routed to A2DP");

                unmuteOutput();

            } else {

                Log.w(TAG,
                        "AudioTrack routed to non-A2DP output");

                muteOutput();
            }
        };

        track.addOnRoutingChangedListener(
                routingListener,
                audioHandler
        );

        // Check whether Bluetooth was already connected.
        requestBluetoothRoute();
    }

    // ---------------------------------------------------------
    // AUDIO THREAD
    // ---------------------------------------------------------

    @Override
    public void run() {

        Process.setThreadPriority(
                Process.THREAD_PRIORITY_URGENT_AUDIO
        );

        Log.i(TAG, "Starting HearingAudio thread");

        if (recorder == null || track == null ||
                recorder.getState() != AudioRecord.STATE_INITIALIZED ||
                track.getState() != AudioTrack.STATE_INITIALIZED) {

            Log.e(TAG, "Audio devices are not initialized");
            releaseResources();
            return;
        }

        byte[] buffer = new byte[recordBufferSize];

        try {

            // Never allow startup playback before route verification.
            muteOutput();

            recorder.startRecording();

            if (recorder.getRecordingState() !=
                    AudioRecord.RECORDSTATE_RECORDING) {

                throw new IllegalStateException(
                        "AudioRecord failed to start"
                );
            }

            track.play();

            started = true;

            // Register after AudioTrack starts.
            registerBluetoothListeners();

            Log.i(TAG, "Audio processing started");

            audioLoop:
            while (!stopped) {

                int bytesRead = recorder.read(
                        buffer,
                        0,
                        buffer.length
                );

                if (bytesRead > 0) {

                    int offset = 0;

                    // AudioTrack.write() may accept only part of a PCM buffer.
                    // Keep writing the unread tail so samples are never dropped.
                    while (offset < bytesRead && !stopped) {
                        int bytesWritten;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            bytesWritten = track.write(
                                    buffer,
                                    offset,
                                    bytesRead - offset,
                                    AudioTrack.WRITE_BLOCKING
                            );
                        } else {
                            bytesWritten = track.write(buffer, offset, bytesRead - offset);
                        }

                        if (bytesWritten <= 0) {
                            Log.e(TAG,
                                    "AudioTrack write error: " + bytesWritten);
                            break audioLoop;
                        }

                        offset += bytesWritten;
                    }

                } else if (bytesRead < 0) {

                    Log.e(TAG,
                            "AudioRecord read error: " + bytesRead);
                    break;
                }
            }

        } catch (Exception e) {

            Log.e(TAG, "Audio processing error", e);

        } finally {

            releaseResources();
        }
    }

    // ---------------------------------------------------------
    // RESOURCE MANAGEMENT
    // ---------------------------------------------------------

    private void releaseRecorder() {

        if (recorder == null) {
            return;
        }

        try {

            if (recorder.getRecordingState() ==
                    AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
            }

        } catch (Exception e) {
            Log.e(TAG, "AudioRecord stop failed", e);
        }

        try {
            recorder.release();
        } catch (Exception e) {
            Log.e(TAG, "AudioRecord release failed", e);
        }

        recorder = null;
    }

    private void releaseResources() {

        Log.i(TAG, "Releasing audio resources");

        stopped = true;
        started = false;

        muteOutput();

        if (audioHandler != null) {
            audioHandler.removeCallbacks(routeCheckRunnable);
        }

        if (audioManager != null && deviceCallback != null) {

            try {
                audioManager.unregisterAudioDeviceCallback(
                        deviceCallback
                );
            } catch (Exception e) {
                Log.e(TAG, "Device callback cleanup failed", e);
            }

            deviceCallback = null;
        }

        if (track != null && routingListener != null) {

            try {
                track.removeOnRoutingChangedListener(
                        routingListener
                );
            } catch (Exception e) {
                Log.e(TAG, "Routing listener cleanup failed", e);
            }

            routingListener = null;
        }

        if (track != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                track.setPreferredDevice(null);
            } catch (Exception e) {
                Log.e(TAG, "Preferred device cleanup failed", e);
            }
        }

        if (enhancer != null) {
            try {
                enhancer.setEnabled(false);
                enhancer.release();
            } catch (Exception e) {
                Log.e(TAG, "Enhancer cleanup failed", e);
            }
            enhancer = null;
        }

        if (noiseSuppressor != null) {
            try {
                noiseSuppressor.setEnabled(false);
                noiseSuppressor.release();
            } catch (Exception e) {
                Log.e(TAG, "NoiseSuppressor cleanup failed", e);
            }
            noiseSuppressor = null;
        }

        if (acousticEchoCanceler != null) {
            try {
                acousticEchoCanceler.setEnabled(false);
                acousticEchoCanceler.release();
            } catch (Exception e) {
                Log.e(TAG, "AEC cleanup failed", e);
            }
            acousticEchoCanceler = null;
        }

        if (equalizer != null) {
            try {
                equalizer.release();
            } catch (Exception e) {
                Log.e(TAG, "Equalizer cleanup failed", e);
            }
            equalizer = null;
        }

        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception e) {
                Log.e(TAG, "Visualizer cleanup failed", e);
            }
            visualizer = null;
        }

        releaseRecorder();

        if (track != null) {

            try {
                if (track.getPlayState() ==
                        AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "AudioTrack stop failed", e);
            }

            try {
                track.release();
            } catch (Exception e) {
                Log.e(TAG, "AudioTrack release failed", e);
            }

            track = null;
        }

        Log.i(TAG, "Audio resources released");
    }

    // ---------------------------------------------------------
    // EXISTING PUBLIC METHODS
    // ---------------------------------------------------------

    public void setLoudnessGain(int gain) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                enhancer != null) {

            enhancer.setTargetGain(gain);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public float getLoudnessGain() {

        return enhancer != null
                ? enhancer.getTargetGain()
                : 0;
    }

    public void setLoudnessEnable(boolean enable) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                enhancer != null) {

            enhancer.setEnabled(enable);
        }
    }

    public boolean getLoudnessEnabled() {

        return enhancer != null && enhancer.getEnabled();
    }

    public void setNoiseSuppressor(boolean enable) {

        if (noiseSuppressor != null) {
            noiseSuppressor.setEnabled(enable);
        }
    }

    public void setAcousticEchoCanceler(boolean enable) {

        if (acousticEchoCanceler != null) {
            acousticEchoCanceler.setEnabled(enable);
        }
    }

    public Equalizer getEqualizer() {
        return equalizer;
    }

    public void setLineBarVisualizer(
            LineBarVisualizer lineBarVisualizer
    ) {
        this.linebarVisualizer = lineBarVisualizer;
    }

    public boolean isStart() {
        return started;
    }

    public boolean isOutputMuted() {
        return outputMuted;
    }

    public void close() {

        Log.i(TAG, "Closing HearingAudio");

        stopped = true;

        // Mute immediately.
        muteOutput();

        if (audioHandler != null) {
            audioHandler.removeCallbacks(routeCheckRunnable);
        }

        // Unblock AudioRecord.read() if necessary.
        if (recorder != null) {
            try {
                if (recorder.getRecordingState() ==
                        AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to stop recorder", e);
            }
        }
    }

    // ---------------------------------------------------------
    // VISUALIZER
    // ---------------------------------------------------------

    @Override
    public void onWaveFormDataCapture(
            Visualizer visualizer,
            byte[] waveform,
            int samplingRate
    ) {

        if (linebarVisualizer != null && waveform != null) {
            linebarVisualizer.setDraw(waveform);
        }
    }

    @Override
    public void onFftDataCapture(
            Visualizer visualizer,
            byte[] fft,
            int samplingRate
    ) {
        // Reserved for FFT processing.
    }
}
