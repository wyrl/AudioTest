package com.example.testme.audiotest;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chibde.visualizer.LineBarVisualizer;

public class MainFragment extends Fragment implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        SeekBar.OnSeekBarChangeListener,
        IPlaying {

    private Context context;
    Button btnStartStop;
    SeekBar seekBarLoudness;
    CheckBox checkBoxLoudness;
    TextView tvLoudness;
    LineBarVisualizer lineBarVisualizer;

    public MainFragment(){
        Config.fragmentPlaying = this;
    }
    public void setContext(Context context){
        this.context = context;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main_fragment, container, false);

        lineBarVisualizer = v.findViewById(R.id.visualizer);

        lineBarVisualizer.setColor(Color.GREEN);

        lineBarVisualizer.setDensity(90f);

        if(Config.hearingAudio != null && Config.hearingAudio.isStart()) {
            Config.hearingAudio.setLineBarVisualizer(lineBarVisualizer);
        }

        btnStartStop = v.findViewById(R.id.btnStartStop);
        btnStartStop.setOnClickListener(this);

        seekBarLoudness = v.findViewById(R.id.seekbarLoudness);

        seekBarLoudness.setOnSeekBarChangeListener(this);
        checkBoxLoudness = v.findViewById(R.id.chkLoudness);
        checkBoxLoudness.setOnCheckedChangeListener(this);

        tvLoudness = v.findViewById(R.id.tvLoudness);

        //GLSurfaceView gl_surface = (GLSurfaceView) v.findViewById(R.id.gl_surface);
        //horizon = new Horizon(gl_surface, getResources().getColor(R.color.background), 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        //horizon.setMaxVolumeDb(120);

        if(Config.hearingAudio != null && Config.hearingAudio.isStart()) {
            checkBoxLoudness.setChecked(Config.hearingAudio.getLoudnessEnabled());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                seekBarLoudness.setProgress((int) Config.hearingAudio.getLoudnessGain(), true);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    seekBarLoudness.setProgress((int) Config.hearingAudio.getLoudnessGain());
                }
            }
            btnStartStop.setText(Config.hearingAudio.isStart() ? "Stop" : "Start");
        }
        else{
            enableAll(false);
        }
        return v;
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.btnStartStop)
            onStartPlaying(v);
    }
    private void onStartPlaying(View v){
        if(btnStartStop.getText().equals("Start")){
            startHearing();

            if(Config.availableDevice.isBluetoothHeadsetConnected() | Config.availableDevice.isUSBHeadsetConnected()) {
                //startHearing();
            }
            else
                Toast.makeText(context, "Please connect bluetooth headset or USB Audio.", Toast.LENGTH_SHORT).show();
        }
        else
        {
            stopHearing();
        }
    }
    private void startHearing(){
        btnStartStop.setText("Stop");
        if (Config.hearingAudio == null || !Config.hearingAudio.isStart()) {
            Config.hearingAudio = new HearingAudio(context);
        }
        Config.hearingAudio.setLineBarVisualizer(lineBarVisualizer);
        Config.hearingAudio.start();
        enableAll(true);
    }



    private void stopHearing(){
        btnStartStop.setText("Start");
        if(Config.playingEqualizer != null)
            Config.playingEqualizer.stop();
        if(Config.hearingAudio != null){
            Config.hearingAudio.close();
            Config.hearingAudio = null;
            checkBoxLoudness.setChecked(false);
            enableAll(false);
            seekBarLoudness.setProgress(0);
        }
    }
    private void enableAll(boolean enabled) {
        checkBoxLoudness.setEnabled(enabled);
        seekBarLoudness.setEnabled(enabled);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(Config.hearingAudio != null){
            if(buttonView.getId() == R.id.chkLoudness){
                seekBarLoudness.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                Config.hearingAudio.setLoudnessEnable(isChecked);
            }
            /*else if(buttonView.getId() == R.id.chkNoise){
                audio.setNoiseSuppressor(isChecked);
            }
            else if(buttonView.getId() == R.id.chkEchoCanceler){
                audio.setAcousticEchoCanceler(isChecked);
            }*/
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        tvLoudness.setText(String.valueOf(progress));
        if(Config.hearingAudio != null)
            Config.hearingAudio.setLoudnessGain(progress);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void start() {
        startHearing();
    }

    @Override
    public void stop() {
        stopHearing();
    }
}
