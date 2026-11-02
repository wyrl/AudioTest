package com.example.testme.audiotest;

import android.content.Context;
import android.graphics.Typeface;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;

public class EqualizerFragment extends Fragment implements CompoundButton.OnCheckedChangeListener, IPlaying {

    short numberFrequencyBands;
    ViewGroup equalizerLayout;
    LinearLayout equalizerLinearLayout;
    private Context context;
    SwitchMaterial swEnable;
    Spinner spinnerPresets;
    public void setContext(Context context){
        this.context = context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.equalizer_fragment, container, false);

        equalizerLayout = v.findViewById(R.id.equalizerLayout);
        swEnable = v.findViewById(R.id.switchEnable);
        swEnable.setOnCheckedChangeListener(this);
        spinnerPresets = v.findViewById(R.id.spinnerPresets);
        /*if(Config.hearingAudio != null) {
            Equalizer equalizer = Config.hearingAudio.getEqualizer();
            numberFrequencyBands = equalizer.getNumberOfBands();
            final short lowerBandLevel = equalizer.getBandLevelRange()[0];
            final short upperBandLevel = equalizer.getBandLevelRange()[1];
            Log.i("EqualizerFragment", "NumberFrequencyBands: " + String.valueOf(numberFrequencyBands));

        }*/
        return v;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(Config.hearingAudio != null && Config.hearingAudio.isStart()) {
            equalizerLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            Config.hearingAudio.getEqualizer().setEnabled(isChecked);
            if(isChecked)
                setupEqualizer();
            else
                equalizerLayout.removeView(equalizerLinearLayout);
        }
        else{
            Toast.makeText(context, "Unable to start audio", Toast.LENGTH_SHORT).show();
            buttonView.setChecked(false);
            equalizerLayout.removeView(equalizerLinearLayout);
        }

    }

    private void setupEqualizer() {
        final Equalizer equalizer = Config.hearingAudio.getEqualizer();
        numberFrequencyBands = equalizer.getNumberOfBands();
        final short lowerBandLevel = equalizer.getBandLevelRange()[0];
        final short upperBandLevel = equalizer.getBandLevelRange()[1];
        Log.i("EqualizerFragment", "NumberFrequencyBands: " + String.valueOf(numberFrequencyBands));

        equalizerLinearLayout = new LinearLayout(context);
        equalizerLinearLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        equalizerLinearLayout.setOrientation(LinearLayout.VERTICAL);

        // Convert 12dp to pixels for card item spacing
        float scale = context.getResources().getDisplayMetrics().density;
        int paddingPx = (int) (16 * scale + 0.5f);
        int marginPx = (int) (12 * scale + 0.5f);

        for (short i = 0; i < numberFrequencyBands; i++) {
            // Material Card Styling Container for each Band
            MaterialCardView rowCardView =
                    new MaterialCardView(context);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, marginPx);
            rowCardView.setLayoutParams(cardParams);
            rowCardView.setCardElevation(scale * 2);
            rowCardView.setRadius(scale * 12);
            rowCardView.setCardBackgroundColor(context.getResources().getColor(R.color.cardBackground));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

            TextView frequencyHeaderTextView = new TextView(context);
            frequencyHeaderTextView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            frequencyHeaderTextView.setGravity(Gravity.LEFT);
            frequencyHeaderTextView.setTextSize(15);
            frequencyHeaderTextView.setTextColor(context.getResources().getColor(R.color.textPrimary));
            frequencyHeaderTextView.setTypeface(null, Typeface.BOLD);
            frequencyHeaderTextView.setText(String.valueOf(equalizer.getCenterFreq(i) / 1000) + " kHz");
            linearLayout.addView(frequencyHeaderTextView);

            LinearLayout seekBarRowLayout = new LinearLayout(context);
            LinearLayout.LayoutParams seekRowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            seekRowParams.setMargins(0, (int)(8 * scale), 0, 0);
            seekBarRowLayout.setLayoutParams(seekRowParams);
            seekBarRowLayout.setOrientation(LinearLayout.HORIZONTAL);
            seekBarRowLayout.setGravity(Gravity.CENTER_VERTICAL);

            TextView minLevelTextView = new TextView(context);
            minLevelTextView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            minLevelTextView.setTextColor(context.getResources().getColor(R.color.textSecondary));
            minLevelTextView.setText(String.valueOf(lowerBandLevel / 100) + " dB");

            TextView maxLevelTextView = new TextView(context);
            maxLevelTextView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            maxLevelTextView.setTextColor(context.getResources().getColor(R.color.textSecondary));
            maxLevelTextView.setText(String.valueOf(upperBandLevel / 100) + " dB");

            SeekBar seekBar = new SeekBar(context);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.weight = 1;
            layoutParams.setMargins((int)(6 * scale), 0, (int)(6 * scale), 0);

            seekBar.setLayoutParams(layoutParams);
            seekBar.setId(i);
            seekBar.setMax(upperBandLevel - lowerBandLevel);
            seekBar.setProgress(equalizer.getBandLevel(i) - lowerBandLevel);

            final short bandIndex = i;
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    equalizer.setBandLevel(bandIndex, (short) (progress + lowerBandLevel));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            seekBarRowLayout.addView(minLevelTextView);
            seekBarRowLayout.addView(seekBar);
            seekBarRowLayout.addView(maxLevelTextView);

            linearLayout.addView(seekBarRowLayout);
            rowCardView.addView(linearLayout);
            equalizerLinearLayout.addView(rowCardView);
        }
        equalizerLayout.addView(equalizerLinearLayout);
        setPresets(equalizer);
    }

    private void setPresets(final Equalizer equalizer) {
        ArrayList<String> presetNames = new ArrayList<>();
        ArrayAdapter<String> presetSpinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, presetNames);
        Log.i("EqualizerFragment", "Number of Presets: " + String.valueOf(equalizer.getNumberOfPresets()));
        for(short i = 0; i < equalizer.getNumberOfPresets(); i++){
            presetNames.add(equalizer.getPresetName(i));
        }
        spinnerPresets.setAdapter(presetSpinnerAdapter);

        spinnerPresets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                equalizer.usePreset((short)position);
                short numberOfFrequencyBands = equalizer.getNumberOfBands();
                final short lowerEqualizerBandLevel = equalizer.getBandLevelRange()[0];

                for(short i = 0; i < numberOfFrequencyBands; i++){
                    SeekBar seekBar = getView().findViewById(i);
                    seekBar.setProgress(equalizer.getBandLevel(i) - lowerEqualizerBandLevel);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        if (swEnable != null) {
            swEnable.setChecked(false);
        }
    }
}
