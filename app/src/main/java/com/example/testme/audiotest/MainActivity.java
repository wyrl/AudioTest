package com.example.testme.audiotest;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;

import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements ActionBar.TabListener, IAudioAvailableDevice {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v13.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        if(!hasPermissions(this, PERMISSIONS)){
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        MainFragment mainFragment = new MainFragment();
        mainFragment.setContext(this);
        Config.playingAudio = mainFragment;
        mSectionsPagerAdapter.addFragment(mainFragment);

        EqualizerFragment equalizerFragment = new EqualizerFragment();
        equalizerFragment.setContext(this);
        Config.playingEqualizer = equalizerFragment;
        mSectionsPagerAdapter.addFragment(equalizerFragment);

        mSectionsPagerAdapter.addFragment(new AudioSettingsFragment());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }

        Config.availableDevice = this;

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);

        filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(usbReciever, filter);

        filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReciever, filter);

    }
    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main2, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction ft) {

    }
    public boolean isBluetoothHeadsetConnected() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()
                && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED;
    }
    public boolean isUSBHeadsetConnected(){
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        if(usbManager.getDeviceList().size() > 0){
            UsbDevice device = (UsbDevice) usbManager.getDeviceList().values().toArray()[0];
            if(device.getDeviceClass() == UsbConstants.USB_CLASS_AUDIO){
                //Toast.makeText(this, "USB Device is not Audio -- " + nameForClass(device) + ", " + String.valueOf(device.getDeviceClass()), Toast.LENGTH_LONG).show();
                return true;
            }
            else if(device.getDeviceClass() == UsbConstants.USB_CLASS_PER_INTERFACE){
                return device.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO;
            }
            else
                Toast.makeText(this, "USB Device is not Audio", Toast.LENGTH_LONG).show();
        }
        return false;
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final int connection_state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            //if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            //Toast.makeText(getApplicationContext(), "On Receive -> State: " + String.valueOf(state), Toast.LENGTH_SHORT).show();
            if(action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)){
                switch (connection_state){
                    case BluetoothAdapter.STATE_CONNECTED:
                        //isBluetoothAudioConnected = true;
                        Toast.makeText(getApplicationContext(), "Bluetooth Headset Connected", Toast.LENGTH_SHORT).show();
                        break;

                    case BluetoothAdapter.STATE_DISCONNECTED:
                        //isBluetoothAudioConnected = false;
                        Toast.makeText(getApplicationContext(), "Bluetooth Headset Disconnected", Toast.LENGTH_SHORT).show();
                        if(!isUSBHeadsetConnected())
                            Config.playingAudio.stop();
                        break;
                }
            }
            else if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                switch (state){
                    case BluetoothAdapter.STATE_OFF:
                        //isBluetoothAudioConnected = false;
                        Toast.makeText(getApplicationContext(), "Bluetooth turn off", Toast.LENGTH_SHORT).show();
                        if(!isUSBHeadsetConnected())
                            Config.playingAudio.stop();
                        break;
                }
            }

        }
    };


    private final BroadcastReceiver usbReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Toast.makeText(getApplicationContext(), "USB Device Attached", Toast.LENGTH_SHORT).show();
            if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)){
                //isUSBAudioConnected = true;
                Toast.makeText(getApplicationContext(), "USB Device Attached", Toast.LENGTH_SHORT).show();
            }
            else if(intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)){
                //isUSBAudioConnected = false;
                Toast.makeText(getApplicationContext(), "USB Device Detached", Toast.LENGTH_SHORT).show();
                if(!isBluetoothHeadsetConnected())
                    Config.playingAudio.stop();
            }
        }
    };
}
