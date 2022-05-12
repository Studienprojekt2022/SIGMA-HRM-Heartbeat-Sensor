package com.example.studienprojekt01;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    LocationManager locationmanager;
    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothServer;

    String MAC = "90:59:AF:1E:5E:37";

    UUID HEART_RATE_SERVICE_UUID = convertFromInteger(0x180D);
    UUID HEART_RATE_MEASUREMENT_CHAR_UUID = convertFromInteger(0x2A37);
    UUID HEART_RATE_CONTROL_POINT_CHAR_UUID = convertFromInteger(0x2A39);
    UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = convertFromInteger(0x2902);

    private ArrayList<BluetoothDevice> scannedDevices = new ArrayList<>();

    public static final int BLUETOOTH_REQ_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button scanButton = findViewById(R.id.scanButton);
        Button connectButton = findViewById(R.id.connectButton);
        TextView deviceTextview = findViewById(R.id.scandevicestextview);
        TextView hrTextview = findViewById(R.id.hrtextview);

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        locationmanager = (LocationManager) getSystemService(LOCATION_SERVICE);

        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.INTERNET}
                                ,10);
                    }
                    return;
                }

                if(!bluetoothAdapter.isEnabled()){
                    Intent bluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(bluetoothIntent, BLUETOOTH_REQ_CODE);
                }

                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();

                scanble();
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                connect();
            }
        });
    }


    public void scanble(){
        TextView deviceTextview = findViewById(R.id.scandevicestextview);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                bleScanner.stopScan(scancallback);
            }
        }, 10000);
        bleScanner.startScan(scancallback);
        if(scannedDevices.isEmpty())
            deviceTextview.setText("NO DEVICES FOUND");
    }




    private ScanCallback scancallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                        TextView deviceTextview = findViewById(R.id.scandevicestextview);
                        if(!scannedDevices.contains(result.getDevice())){
                            if(scannedDevices.isEmpty())
                                deviceTextview.setText("");
                            scannedDevices.add(result.getDevice());
                            deviceTextview.setText(deviceTextview.getText() + " " + result.getDevice().getName() + " " + result.getDevice() + "\n");
                        }
                    }
            };

    public void connect() {
        BluetoothDevice bluetoothDevice=bluetoothAdapter.getRemoteDevice(MAC);
        if(bluetoothDevice!=null) {
            bluetoothServer = bluetoothDevice.connectGatt(this, true, serverCallback);
        }
    }

    private BluetoothGattCallback serverCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    if(BluetoothProfile.STATE_CONNECTED == newState) {
                        gatt.discoverServices();
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    BluetoothGattCharacteristic characteristic = gatt.getService(HEART_RATE_SERVICE_UUID)
                            .getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID);

                    gatt.setCharacteristicNotification(characteristic, true);
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);

                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    processData(characteristic.getValue(), gatt);
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    BluetoothGattCharacteristic characteristic =
                            gatt.getService(HEART_RATE_SERVICE_UUID)
                                    .getCharacteristic(HEART_RATE_CONTROL_POINT_CHAR_UUID);

                    characteristic.setValue(new byte[]{1, 1});
                    gatt.writeCharacteristic(characteristic);
                }
            };

    public void processData(byte[] data, BluetoothGatt gatt) {
        BluetoothGattCharacteristic characteristic = gatt.getService(HEART_RATE_SERVICE_UUID)
                .getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID);

        if (HEART_RATE_MEASUREMENT_CHAR_UUID.equals(characteristic.getUuid())) {
            int FORMAT_UINT = getFormat(characteristic);
            final int heartRate = characteristic.getIntValue(FORMAT_UINT, 1);
            displayHR(heartRate);
        }
    }

    private void displayHR(int HR) {
        ((TextView) findViewById(R.id.hrtextview)).invalidate();
        ((TextView) findViewById(R.id.hrtextview)).setText(HR+"");
    }

    private int getFormat(BluetoothGattCharacteristic characteristic) {
        int characteristicProperties = characteristic.getProperties();
        int ret;

        if ((characteristicProperties & 0x01) != 0) {
            ret = BluetoothGattCharacteristic.FORMAT_UINT16;
        } else {
            ret = BluetoothGattCharacteristic.FORMAT_UINT8;
        }
        return ret;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Toast.makeText(MainActivity.this, "Bluetooth is ON", Toast.LENGTH_SHORT).show();
        } else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(MainActivity.this, "Bluetooth operation is cancelled",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public UUID convertFromInteger(int i) {
        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805f9b34fbL;
        long value = i & 0xFFFFFFFF;
        return new UUID(MSB | (value << 32), LSB);
    }
}