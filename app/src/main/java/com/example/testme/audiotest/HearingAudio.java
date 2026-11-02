package com.example.testme.audiotest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
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
import android.os.Process;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.chibde.visualizer.LineBarVisualizer;

public class HearingAudio extends Thread implements Visualizer.OnDataCaptureListener {
    private static final String TAG = "HearingAudio";

    private volatile boolean stopped = false;
    private volatile boolean started = false;

    private int minBufferSize;
    private int sampleRateInHz;

    private AudioRecord recorder = null;
    private AudioTrack track = null;

    private LoudnessEnhancer enhancer;
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler acousticEchoCanceler;
    private Equalizer equalizer;
    private Visualizer visualizer;

    private LineBarVisualizer linebarVisualizer;
    private final Context context;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public HearingAudio(Context context) {
        this.context = context;

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int defaultSampleRate = 44100;

        if (am != null) {
            String sampleRateStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            if (sampleRateStr != null) {
                try {
                    defaultSampleRate = Integer.parseInt(sampleRateStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse sample rate", e);
                }
            }
            am.setMode(AudioManager.MODE_NORMAL);
        }
        this.sampleRateInHz = defaultSampleRate;

        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void init() {
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int channelIn = AudioFormat.CHANNEL_IN_STEREO;
        int channelOut = AudioFormat.CHANNEL_OUT_STEREO;

        minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelIn, encoding);
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            minBufferSize = sampleRateInHz * 2;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted");
            return;
        }

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInHz,
                channelIn,
                encoding,
                minBufferSize
        );

        // Modern AudioTrack Initialization using AudioAttributes and AudioFormat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(encoding)
                            .setSampleRate(sampleRateInHz)
                            .setChannelMask(channelOut)
                            .build())
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } else {
            track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRateInHz,
                    channelOut,
                    encoding,
                    minBufferSize,
                    AudioTrack.MODE_STREAM
            );
        }

        setupAudioEffects();
    }



    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void setupAudioEffects() {
        int trackSessionId = track.getAudioSessionId();
        int recordSessionId = recorder.getAudioSessionId();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                enhancer = new LoudnessEnhancer(trackSessionId);
                enhancer.setEnabled(false);
            } catch (Exception e) {
                Log.e(TAG, "LoudnessEnhancer init failed", e);
            }
        }

        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(recordSessionId);
            } catch (Exception e) {
                Log.e(TAG, "NoiseSuppressor init failed", e);
            }
        }

        if (AcousticEchoCanceler.isAvailable()) {
            try {
                acousticEchoCanceler = AcousticEchoCanceler.create(recordSessionId);
            } catch (Exception e) {
                Log.e(TAG, "AcousticEchoCanceler init failed", e);
            }
        }

        try {
            equalizer = new Equalizer(0, trackSessionId);
        } catch (Exception e) {
            Log.e(TAG, "Equalizer init failed", e);
        }

        try {
            visualizer = new Visualizer(trackSessionId);
            visualizer.setDataCaptureListener(this, Visualizer.getMaxCaptureRate() / 2, true, false);
            visualizer.setEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Visualizer init failed", e);
        }
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        Log.i(TAG, "Running HearingAudio Thread");

        if (recorder == null || track == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord or AudioTrack not properly initialized");
            return;
        }

        byte[] buffer = new byte[minBufferSize];
        recorder.startRecording();
        track.play();
        started = true;

        try {
            while (!stopped) {
                int bytesRead = recorder.read(buffer, 0, minBufferSize);
                if (bytesRead > 0) {
                    track.write(buffer, 0, bytesRead);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in audio loop", e);
        } finally {
            releaseResources();
        }
    }

    private void releaseResources() {
        started = false;
        try {
            if (enhancer != null) {
                enhancer.setEnabled(false);
                enhancer.release();
            }
            if (noiseSuppressor != null) {
                noiseSuppressor.setEnabled(false);
                noiseSuppressor.release();
            }
            if (acousticEchoCanceler != null) {
                acousticEchoCanceler.setEnabled(false);
                acousticEchoCanceler.release();
            }
            if (equalizer != null) {
                equalizer.release();
            }
            if (visualizer != null) {
                visualizer.setEnabled(false);
                visualizer.release();
            }
            if (recorder != null) {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
                recorder.release();
            }
            if (track != null) {
                if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop();
                }
                track.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing audio resources", e);
        }
    }

    public void setLoudnessGain(int gain) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && enhancer != null) {
            enhancer.setTargetGain(gain);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public float getLoudnessGain() {
        return (enhancer != null) ? enhancer.getTargetGain() : 0;
    }

    public void setLoudnessEnable(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && enhancer != null) {
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

    public void setLineBarVisualizer(LineBarVisualizer lineBarVisualizer) {
        this.linebarVisualizer = lineBarVisualizer;
    }

    public boolean isStart() {
        return started;
    }

    public void close() {
        stopped = true;
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
        if (linebarVisualizer != null) {
            linebarVisualizer.setDraw(waveform);
        }
    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
    }

    public int getAudioSessionId() {
        return recorder.getAudioSessionId();
    }
}