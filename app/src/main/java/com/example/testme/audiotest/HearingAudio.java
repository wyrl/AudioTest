package com.example.testme.audiotest;

import android.content.Context;
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
import android.os.Process;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.chibde.BaseVisualizer;
import com.chibde.visualizer.LineBarVisualizer;


/**
 * Created by testme on 4/5/2017.
 */
public class HearingAudio extends Thread implements Visualizer.OnDataCaptureListener {
    private boolean stopped = false;
    private boolean started = false;
    private int minBufferSize;
    private final int standard_samplerate = 44100;
    private int default_deviceSampleRate;
    private int sampleRateInHz;
    AudioRecord recorder = null;
    AudioTrack track = null;
    private int channel_in = AudioFormat.CHANNEL_IN_DEFAULT;
    private int channel_out = AudioFormat.CHANNEL_OUT_DEFAULT;
    public final int CHANNEL_DEFAULT = 0;
    public final int CHANNEL_MONO = 1;
    public final int CHANNEL_STEREO = 2;

    LoudnessEnhancer enhancer;
    NoiseSuppressor noiseSuppressor;
    AcousticEchoCanceler acousticEchoCanceler;

    public final int SAMPLE_RATE_STANDARD = 0;
    public final int SAMPLE_RATE_MEDIUM = 1;
    public final int SAMPLE_RATE_HIGH = 2;
    public final int SAMPLE_RATE_BEST = 3;

    private int [] sampleRateInHzArr = {
            44100,
            48000,
            88200,
            96000,
            192000
    };
    private LineBarVisualizer linebarVisualizer;
    private Visualizer visualizer;
    private Equalizer equalizer;

    public HearingAudio(Context context)
    {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String sampleRateStr = "0";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            sampleRateStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        }
        default_deviceSampleRate = Integer.parseInt(sampleRateStr);
        if (default_deviceSampleRate == 0)
            default_deviceSampleRate = standard_samplerate;
        sampleRateInHz = default_deviceSampleRate;

        //am.setMode(AudioManager.);

        am.setWiredHeadsetOn(true);
        am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_NORMAL);
        //am.setBluetoothScoOn(true);
        //am.startBluetoothSco();
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        init();
    }
    private void init(){
        int audio_format_encoding = AudioFormat.ENCODING_PCM_16BIT;
        int audio_format_channel_in = AudioFormat.CHANNEL_IN_STEREO;
        int audio_format_channel_out = AudioFormat.CHANNEL_OUT_STEREO;

        //sampleRateInHz = 44100;

        minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_IN_STEREO, audio_format_encoding);
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInHz,
                audio_format_channel_in,
                audio_format_encoding,
                minBufferSize);

        track = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRateInHz,
                audio_format_channel_out,
                audio_format_encoding,
                minBufferSize,
                AudioTrack.MODE_STREAM);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            enhancer = new LoudnessEnhancer(track.getAudioSessionId());
            enhancer.setEnabled(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            noiseSuppressor = NoiseSuppressor.create(recorder.getAudioSessionId());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            acousticEchoCanceler = AcousticEchoCanceler.create(recorder.getAudioSessionId());
        }

        equalizer = new Equalizer(0, track.getAudioSessionId());

        visualizer = new Visualizer(track.getAudioSessionId());
        visualizer.setDataCaptureListener(this, Visualizer.getMaxCaptureRate() / 2, true, false);
        visualizer.setEnabled(true);

        track.setPlaybackRate(sampleRateInHz);
    }
    public void setSampleRateMask(int mask){
        sampleRateInHz = sampleRateInHzArr[mask];
    }
    public void setChannel(int channel){
        switch (channel){
            case CHANNEL_DEFAULT:
                channel_in = AudioFormat.CHANNEL_IN_DEFAULT;
                channel_out = AudioFormat.CHANNEL_OUT_DEFAULT;
                break;
            case CHANNEL_MONO:
                channel_in = AudioFormat.CHANNEL_IN_MONO;
                channel_out = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case CHANNEL_STEREO:
                channel_in = AudioFormat.CHANNEL_IN_STEREO;
                channel_out = AudioFormat.CHANNEL_OUT_STEREO;
        }
    }
    public void setLoudnessGain(int gain){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            enhancer.setTargetGain(gain);
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public float getLoudnessGain(){
        return enhancer.getTargetGain();
    }
    public void setLoudnessEnable(boolean enable){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            enhancer.setEnabled(enable);
        }
    }
    public boolean getLoudnessEnabled(){
        return enhancer.getEnabled();
    }

    public void setLineBarVisualizer(LineBarVisualizer lineBarVisualizer){
        this.linebarVisualizer = lineBarVisualizer;
    }


    public void setNoiseSuppressor(boolean enable){
        noiseSuppressor.setEnabled(enable);
    }
    public void setAcousticEchoCanceler(boolean enable){
        acousticEchoCanceler.setEnabled(enable);
    }

    public Equalizer getEqualizer(){
        return equalizer;
    }

    @Override
    public void run() {
        Log.i("HearingAudio", "Running HearingAudio Thread");
        byte [] buffer = new byte[minBufferSize];
        recorder.startRecording();
        track.play();
        started = true;
        try
        {
            int N = 0;
            while(!stopped){
                N = recorder.read(buffer, 0, minBufferSize);
                track.write(buffer, 0, N);
            }
        }
        catch (Throwable x)
        {
            Log.e("AudioError", "Error reading voice audio", x);
        }
        finally {
            enhancer.setEnabled(false);
            if(noiseSuppressor != null)
                noiseSuppressor.setEnabled(false);
            recorder.stop();
            recorder.release();
            track.stop();
            track.release();
            equalizer.release();
            visualizer.release();
        }
    }
    public int getMinBufferSize()
    {
        return minBufferSize;
    }
    public float getMaxVolume()
    {
        return track.getMaxVolume();
    }

    public boolean isStart(){
        return started;
    }

    public void close(){
        stopped = true;
    }

    @Override
    public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
        if(linebarVisualizer != null)
            linebarVisualizer.setDraw(waveform);
    }

    @Override
    public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {

    }
}
