package com.example.testme.audiotest;


import android.support.v13.app.FragmentPagerAdapter;
import android.app.Fragment;
import android.app.FragmentManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class SectionsPagerAdapter extends FragmentPagerAdapter {
    String [] titles = {
            "Main",
            "Equalizer",
            "Audio Settings"
    };
    ArrayList<Fragment> fragments;

    public SectionsPagerAdapter(FragmentManager fm) {
        super(fm);
        fragments = new ArrayList<>();
    }
    public void addFragment(Fragment fragment){
        fragments.add(fragment);
    }

    @Override
    public Fragment getItem(int position) {
        Log.i("SectionsPagerAdapter", "Position: " + String.valueOf(position));
        return fragments.get(position);
    }

    @Override
    public int getCount() {
        return 3;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return titles[position];
    }
}
