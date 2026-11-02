package com.example.testme.audiotest;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements IAudioAvailableDevice {

    private SectionsPagerAdapter sectionsPagerAdapter;
    private boolean startAfterPermissionGrant;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = hasRequiredPermissions();
                if (!allGranted) {
                    Toast.makeText(this, "Permissions required for audio processing", Toast.LENGTH_SHORT).show();
                } else if (startAfterPermissionGrant) {
                    startAfterPermissionGrant = false;
                    requestHearingServiceStart();
                }
            });

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        checkAndRequestPermissions();

        ViewPager2 viewPager = findViewById(R.id.container);
        TabLayout tabLayout = findViewById(R.id.tabs);

        sectionsPagerAdapter = new SectionsPagerAdapter(this);
        initMainFragment();
        initEqualizer();
        sectionsPagerAdapter.addFragment(new AudioSettingsFragment(), "Settings");

        viewPager.setAdapter(sectionsPagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(sectionsPagerAdapter.getPageTitle(position))
        ).attach();

        Config.availableDevice = this;

        IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        btFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        btFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this, bluetoothReceiver, btFilter, ContextCompat.RECEIVER_EXPORTED);

        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        ContextCompat.registerReceiver(this, usbReceiver, usbFilter, ContextCompat.RECEIVER_EXPORTED);

        findTargetBluetoothDevice();
    }

    private void startHearingService() {
        if (!hasRequiredPermissions()) {
            startAfterPermissionGrant = true;
            checkAndRequestPermissions();
            return;
        }

        Intent serviceIntent = new Intent(this, HearingService.class);
        serviceIntent.setAction(HearingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    public boolean requestHearingServiceStart() {
        if (!hasRequiredPermissions()) {
            startAfterPermissionGrant = true;
            checkAndRequestPermissions();
            return false;
        }

        if (!HearingService.isBluetoothAudioDeviceConnected(this)) {
            Toast.makeText(this, "Connect an A2DP Bluetooth headset to start hearing.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        startHearingService();
        return true;
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = getStrings();

        List<String> ungrantedPermissions = new ArrayList<>();
        for (String perm : permissionsToRequest) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ungrantedPermissions.add(perm);
            }
        }

        if (!ungrantedPermissions.isEmpty()) {
            requestPermissionLauncher.launch(ungrantedPermissions.toArray(new String[0]));
        }
    }

    private boolean hasRequiredPermissions() {
        for (String permission : getStrings()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static List<String> getStrings() {
        List<String> permissionsToRequest = new ArrayList<>();
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return permissionsToRequest;
    }

    private void initMainFragment() {
        MainFragment mainFragment = new MainFragment();
        Config.fragmentPlaying = mainFragment;
        sectionsPagerAdapter.addFragment(mainFragment, "Main");
    }

    private void initEqualizer() {
        EqualizerFragment equalizerFragment = new EqualizerFragment();
        equalizerFragment.setContext(this);
        Config.playingEqualizer = equalizerFragment;
        sectionsPagerAdapter.addFragment(equalizerFragment, "Equalizer");
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void findTargetBluetoothDevice() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
        String deviceName = "Rapoo H6020";
        if (devices != null) {
            for (BluetoothDevice dev : devices) {
                if (deviceName.equals(dev.getName())) {
                    Toast.makeText(this, deviceName, Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean isBluetoothHeadsetConnected() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        return bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED;
    }

    public boolean isUSBHeadsetConnected() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        if (usbManager != null && !usbManager.getDeviceList().isEmpty()) {
            for (UsbDevice dev : usbManager.getDeviceList().values()) {
                if (dev.getDeviceClass() == UsbConstants.USB_CLASS_AUDIO) {
                    return true;
                } else if (dev.getDeviceClass() == UsbConstants.USB_CLASS_PER_INTERFACE) {
                    if (dev.getInterfaceCount() > 0 && dev.getInterface(0).getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i("BluetoothReceiver", "Received action: " + action);
            if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    Toast.makeText(getApplicationContext(), "Bluetooth A2DP connected", Toast.LENGTH_SHORT).show();
                    requestHearingServiceStart();
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    Toast.makeText(getApplicationContext(), "Bluetooth A2DP disconnected", Toast.LENGTH_SHORT).show();
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    Toast.makeText(getApplicationContext(), "Bluetooth turned off", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Toast.makeText(getApplicationContext(), "USB Device Attached", Toast.LENGTH_SHORT).show();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Toast.makeText(getApplicationContext(), "USB Device Detached", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main2, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(bluetoothReceiver);
            unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Receiver not registered", e);
        }
    }
}
