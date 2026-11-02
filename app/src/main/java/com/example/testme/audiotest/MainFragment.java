package com.example.testme.audiotest;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.chibde.visualizer.LineBarVisualizer;
import com.google.android.material.button.MaterialButton;

public class MainFragment extends Fragment implements IPlaying {
    private LineBarVisualizer visualizer;
    private CheckBox chkLoudness;
    private TextView tvLoudness;
    private SeekBar seekbarLoudness;
    private MaterialButton btnStartStop;
    private boolean isServiceRunning = false;

    public MainFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main_fragment, container, false);
        visualizer = v.findViewById(R.id.visualizer);
        visualizer.setColor(Color.parseColor("#00B0FF"));
        visualizer.setDensity(70f);
        chkLoudness = v.findViewById(R.id.chkLoudness);
        tvLoudness = v.findViewById(R.id.tvLoudness);
        seekbarLoudness = v.findViewById(R.id.seekbarLoudness);
        btnStartStop = v.findViewById(R.id.btnStartStop);
        isServiceRunning = Config.hearingAudio != null && Config.hearingAudio.isStart();
        btnStartStop.setText(isServiceRunning ? "STOP" : "START");
        if (isServiceRunning) start();
        setupLoudnessControls();
        btnStartStop.setOnClickListener(view -> {
            if (isServiceRunning) stopHearingAidService();
            else startHearingAidService();
        });
        return v;
    }

    private void setupLoudnessControls() {
        chkLoudness.setOnCheckedChangeListener((buttonView, isChecked) -> {
            seekbarLoudness.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (Config.hearingAudio != null) Config.hearingAudio.setLoudnessEnable(isChecked);
        });
        seekbarLoudness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLoudness.setText(String.valueOf(progress));
                if (fromUser && Config.hearingAudio != null) Config.hearingAudio.setLoudnessGain(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void startHearingAidService() {
        if (getContext() == null || !HearingService.isBluetoothAudioDeviceConnected(getContext())) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Connect your Bluetooth headset to start hearing.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        isServiceRunning = true;
        btnStartStop.setText("STOP");
        if (getActivity() != null) {
            Intent serviceIntent = new Intent(getActivity(), HearingService.class);
            serviceIntent.setAction(HearingService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getActivity().startForegroundService(serviceIntent);
            } else {
                getActivity().startService(serviceIntent);
            }
        }
    }

    private void stopHearingAidService() {
        isServiceRunning = false;
        btnStartStop.setText("START");
        if (getActivity() != null) {
            Intent serviceIntent = new Intent(getActivity(), HearingService.class);
            serviceIntent.setAction(HearingService.ACTION_STOP);
            getActivity().startService(serviceIntent);
        }
        stop();
    }

    @Override
    public void start() {
        if (visualizer != null && Config.hearingAudio != null) {
            Config.hearingAudio.setLineBarVisualizer(visualizer);
        }
    }

    @Override
    public void stop() {
        if (Config.hearingAudio != null) {
            Config.hearingAudio.setLineBarVisualizer(null);
        }
        if (visualizer != null) visualizer.clearBytes();
    }

    @Override
    public void onDestroyView() {
        stop();
        if (Config.fragmentPlaying == this) Config.fragmentPlaying = null;
        super.onDestroyView();
    }

}
