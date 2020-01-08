package com.tandd.tr4blesample;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;
import static com.tandd.tr4blesample.R.id.dataTime;
import static com.tandd.tr4blesample.R.id.devName;

public class MainActivity extends AppCompatActivity {

    private DeviceList mDeviceList ;
    private ListView mListView = null ;
    BluetoothManager mBluetoothManager ;
    BluetoothAdapter mBluetoothAdapter ;
    BluetoothLeScanner mBluetoothLeScanner ;
    private long mScanTime ;

    private Timer mScanStop ;  // Timer to stop the search

    private final static int REQUEST_PERMISSIONS = 1;
    private final static int SDKVER_MARSHMALLOW = 23;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mListView = (ListView)findViewById(R.id.devlist) ;
        mDeviceList = new DeviceList();

        Sb_Init_Ble_Scan() ;

        // [Search for TR4] Button
        findViewById(R.id.btn_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDeviceList.RefreshList() ;
                mDeviceList.DeleteAllItem() ;
                Sb_Scan() ; // Starts the search for the BLE device
            }
        }) ;

        // Displays the context menu by press and hold
        registerForContextMenu(mListView) ;

        // Checks Bluetooth permission for Android 6.0 or later
        if(Build.VERSION.SDK_INT >= SDKVER_MARSHMALLOW)
            this.requestBlePermission();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

        super.onCreateContextMenu(menu, v, menuInfo) ;

        //Context Menu Setting
        menu.add(0, 100, 0, "Get Current Readings") ;
        menu.add(0, 101, 0, "Download Data") ;
    }

    @TargetApi(SDKVER_MARSHMALLOW)
    private void requestBlePermission(){
        // Requests permission if not granted
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION
            },REQUEST_PERMISSIONS) ;
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo() ;
        BluetoothDevice btdv = mDeviceList.infoList.get(info.position).getBluetoothDevice() ;

        IntentInterface ii ;
        Intent intent ;

        switch (item.getItemId()) {
            case 100:
                // Get Current Readings
                ii = new IntentInterface() ;
                ii.SetBLEDevice(btdv) ;
                ii.SetParam(MainActivity.this) ;
                intent = new Intent(MainActivity.this, GetCurrentActivity.class) ;
                intent.putExtra("IntentInterface", ii) ;
                startActivity(intent) ;
                return true ;
            case 101:
                // Download Data
                ii = new IntentInterface() ;
                ii.SetBLEDevice(btdv) ;
                ii.SetParam(MainActivity.this) ;
                intent = new Intent(MainActivity.this, DownloadActivity.class) ;
                intent.putExtra("IntentInterface", ii) ;
                startActivity(intent) ;
                return true ;
            default:
                return super.onContextItemSelected(item) ;
        }
    }


    // ************************************************************************
    // void Sb_Init_Ble_Scan()
    // Initialization of Bluetooth
    // Argument  :IN :
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Init_Ble_Scan()
    {
        if(mBluetoothAdapter != null)
            return ;

        //Gets a BluetoothAdapter
        BluetoothAdapter btAdpt = BluetoothAdapter.getDefaultAdapter() ;
        if(btAdpt != null) {
            // For Bluetooth compatible devices
        } else {
            // For Bluetooth incompatible devices
            finish() ;
        }

        boolean btEnable = btAdpt != null && btAdpt.isEnabled() ;
        if(btEnable == true) {
            // Makes a Bluetooth Adapter
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE) ;
            mBluetoothAdapter = mBluetoothManager.getAdapter() ;
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner() ;
        }
        else {
            // Displays a dialog prompting the user to turn ON the Bluetooth if it is OFF.
            Intent btOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE) ;
            startActivityForResult(btOn, 1) ;
        }
    }

    // ************************************************************************
    // OnScanStopTimer
    // Timer to stop the search for the BLE device
    // ************************************************************************
    public class OnScanStopTimer extends TimerTask
    {
        @Override
        public void run(){
            mBluetoothLeScanner.stopScan(mScanCallback) ;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Search related functions
    /////////////////////////////////////////////////////////////////////////////

    /** Searches for the BLE device */
    private void Sb_Scan()
    {
        Sb_Init_Ble_Scan() ;

        mScanTime = new Date().getTime() ;

        // Makes the scan filter
        List<ScanFilter> mScanFilters = new ArrayList<>() ;
        // Makes the scan mode
        ScanSettings.Builder mScanSettingBuiler = new ScanSettings.Builder() ;
        mScanSettingBuiler.setScanMode(SCAN_MODE_LOW_LATENCY) ;
        ScanSettings mScanSettings = mScanSettingBuiler.build() ;
        // Starts scanning using the made scan filter and scan mode.
        mBluetoothLeScanner.startScan(mScanFilters, mScanSettings, mScanCallback) ;


        // Stops in 5 seconds
        mScanStop = new Timer() ;
        TimerTask timerTask = new OnScanStopTimer() ;
        mScanStop.schedule(timerTask, 5000) ;
    }

    private ScanCallback mScanCallback = new ScanCallback()
    {

        public void onBatchScanResults(List<ScanResult> results) {
        };

        public void onScanFailed(int errorCode) {
        };

        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice() ;
            byte[] scanRecord = result.getScanRecord().getBytes() ;

            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                long serial[] = new long[1] ;
                int security[] = new int[1] ;
                int ch1Data[] = new int[1] ;
                int ch2Data[] = new int[1] ;
                int batLevel[] = new int[1] ;
                StringBuffer localName = new StringBuffer() ;
                int found ;
                int rtn ;

                String devName = device.getName() ;
                // if(devName == null) return ;

                // If you want to search for only nearby TR4
                //if(result.getRssi() < -50)
                //    return ;

                rtn = Sb_Parse_ScanRecord(scanRecord, serial, localName, security, ch1Data, ch2Data, batLevel) ;
                if(rtn == 0) {
                    devName = localName.toString().trim() ;

                    // Checks the registered serial number
                    found = 0 ;
                    for(int i=0; i< mDeviceList.getCount(); i++) {
                        if(mDeviceList.infoList.get(i).getSerial() == serial[0]) {
                            found = 1 ;
                            break ;
                        }
                    }

                    if(found == 0) {
                        mDeviceList.addDeviceInfo(serial[0], devName, device.getAddress(),
                                                    device, ch1Data[0], mScanTime/1000);

                        mListView.setAdapter(mDeviceList) ;
                        mDeviceList.notifyDataSetChanged() ;
                    }

                }
            }
        };
    };


    // ************************************************************************
    // int Sb_Parse_ScanRecord(final byte[] scanRecord, long serialNo[],
    //                         StringBuffer devName, int security[],
    //                         int ch1Data[], int ch2Data[], int batLevel[])
    // Analyzes the scanned data whether it is a TR4 series or not
    // Argument  :IN : scanRecord  : Scanned data
    //       :OUT:  serialNo    : Device Serial Number
    //             devName     : Device Name
    //             security    : Device Lock
    //             ch1Data     : Ch1 Data (raw data)
    //             ch2Data     : Ch2 Data (raw data) = Not in use
    //             batLevel    : Battery Level (1-5)
    //       :RET:              : 0 = TR4 Series  -1 = different device
    // ************************************************************************
    private int Sb_Parse_ScanRecord(final byte[] scanRecord, long serialNo[],
                                    StringBuffer devName, int security[],
                                    int ch1Data[], int ch2Data[], int batLevel[])
    {
        System.out.println("Scan Record" + scanRecord);
        // [Length][AD Type][Data][Length][AD Type][Data]
        //    02     01     06     1b      ff     .....
        int pt = 0 ;
        int companyCode ; // Company Code
        int formatCode ; // Data Format Number

        try {
            pt = (int)scanRecord[0] + 1 ;

            // Company Code
            pt += 2 ;
            companyCode = Sb_Conv2ByteToInt(scanRecord[pt], scanRecord[pt+1]) ;
            // if(companyCode != 0x0392) return -1 ;

            // Serial Number
            pt += 2 ;
            serialNo[0] = Sb_Conv4ByteToLong(scanRecord[pt], scanRecord[pt+1], scanRecord[pt+2], scanRecord[pt+3]) ;
            int devType = Sb_Get_DeviceType(serialNo[0]) ;
            // if(devType != 0x16 && devType != 0x17 && devType != 0x18) return -1 ;

            // Device Lock
            pt += 4 ;
            security[0] = (int)scanRecord[pt] ;

            // Data Format Number
            pt += 1 ;
            formatCode = (int)scanRecord[pt] ;
            // if(formatCode != 1) return -1 ;

            // Ch1 Data
            pt ++ ;
            ch1Data[0] = Sb_Conv2ByteToInt(scanRecord[pt], scanRecord[pt+1]) ;

            // Ch2 Data (Not in use)
            pt += 2 ;
            ch2Data[0] = Sb_Conv2ByteToInt(scanRecord[pt], scanRecord[pt+1]) ;

            // Battery Level
            pt += 2 ;
            batLevel[0] = (int)scanRecord[pt] ;

            // Device Name
            devName.append(Sb_Get_Str_from_Byte(scanRecord, 33, 33+16, "UTF-8")) ;

            System.out.println("Device Name is" + devName);

            return 0 ;
        }
        catch (Exception e) {
            e.printStackTrace();
            return -1 ;
        }

    }

    // ************************************************************************
    // int Sb_Conv2ByteToInt(byte b1, byte b2)
    // Converts 2-byte unsigned little endian to int
    // Argument  :IN :  b1      : 1st byte
    //              b2      : 2nd byte
    //       :OUT:
    //       :RET:           : Value after conversion
    // ************************************************************************
    private int Sb_Conv2ByteToInt(byte b1, byte b2)
    {
        int c1, c2 ;

        c1 = b1 ;
        c2 = b2 ;

        c1 = c1 & 0xFF ;
        c2 = c2 & 0xFF ;

        int val = c1 + (c2 << 8) ;

        return val ;
    }

    // ************************************************************************
    // int Sa_Conv4ByteToLong(byte b1, byte b2, byte b3, byte b4)
    // Converts 4-byte unsigned little endian to int
    // Argument  :IN :  b1      : 1st byte
    //              b2      : 2nd byte
    //              b3      : 3rd byte
    //              b4      : 4th byte
    //       :OUT:
    //       :RET:           : Value after conversion
    // ************************************************************************
    private long Sb_Conv4ByteToLong(byte b1, byte b2, byte b3, byte b4)
    {
        long c1, c2, c3, c4 ;

        c1 = b1 ;
        c2 = b2 ;
        c3 = b3 ;
        c4 = b4 ;

        c1 = c1 & 0xFF ;
        c2 = c2 & 0xFF ;
        c3 = c3 & 0xFF ;
        c4 = c4 & 0xFF ;

        long val = c1 + (c2 << 8) + (c3 << 16) + (c4 << 24) ;

        return val ;
    }

    // ************************************************************************
    // int Sa_Get_DeviceType(long serial)
    // Returns the TR4's device type from the argument's serial number.
    // Argument  :IN :  serial      : Serial Number
    //       :OUT:
    //       :RET:               : TR41=0x16 TR42=0x17 TR45=0x18
    // ************************************************************************
    private int Sb_Get_DeviceType(long serial)
    {
        return (int) ((serial >> 17) & 0x7F) ;
    }

    // ************************************************************************
    // String Sb_Get_Str_from_Byte(final byte[] data, int start, int size,
    //                              String charsetName)
    // Returns the character string of the specified size from the argument byte string.[KA1]
    // Argument  :IN :  data         : byte[]
    //              start        : Start position
    //              size         : Specified size (in bytes)
    //              charsetName  : Character code ("SJIS","UTF-8", etc.)
    //       :OUT:
    //       :RET:                : Return value as character string
    // ************************************************************************
    private String Sb_Get_Str_from_Byte(final byte[] data, int start,
                                        int size, String charsetName)
    {
        byte[] bTmp = new byte[size] ;
        String stTmp ;
        int i ;
        int c = 0 ;

        for(i=start; i<start+size; i++) {
            if(data[i] == 0x00)
                break ;
            else
                bTmp[c] = data[i] ;
            c++ ;
        }

        try {
            byte[] bStr = new byte[c] ;
            for(i=0; i<bStr.length; i++)
                bStr[i] = bTmp[i] ;
            stTmp = new String(bStr, charsetName) ;
        } catch (UnsupportedEncodingException e) {
            return "" ;
        }

        return stTmp ;
    }

    // ************************************************************************
    // String Sb_GetTimeStr(long timeData, String format)
    // Returns a formatted time string converted from UNIX time
    // Argument  :IN :  timeData    : UNIX time (in seconds)
    //              format      : Date and Time Format (e.g. "MMM dd,yyyy HH:mm:ss" )
    //       :OUT:
    //       :RET:               : Time String
    // ************************************************************************
    private String Sb_GetTimeStr(long timeData, String format)
    {
        String retStr = "" ;

        SimpleDateFormat sdf = new SimpleDateFormat(format) ;
        Date orgTime = new Date(timeData*1000) ;
        retStr = sdf.format(orgTime) ;

        return retStr ;
    }



    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Adapter to store the device list
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private  class DeviceList extends BaseAdapter {

        private List<DeviceInfo> infoList;

        private DeviceList() {
            infoList = new ArrayList<DeviceInfo>();
        }

        public void addDeviceInfo(long serial, String name, String address, BluetoothDevice bltDevice,
                                  int ch1Data, long dataTime)
        {
            if(name == null)
                return ;

            infoList.add(new DeviceInfo(serial, name, address, bltDevice, ch1Data, dataTime)) ;
        }

        public void updataDeviceInfo(long serial, int ch1Data, long dataTime, int rssi) {
            for(int i=0; i<infoList.size(); i++) {
                if(infoList.get(i).getSerial() == serial) {
                    infoList.get(i).ch1Data = ch1Data ;
                    infoList.get(i).dataTime = dataTime ;
                    break ;
                }
            }
        }

        @Override
        public int getCount() {
            return infoList.size() ;
        }

        @Override
        public Object getItem(int position) {
            return infoList.get(position) ;
        }

        @Override
        public long getItemId(int position) {
            return position ;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView ;
            if(v == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE) ;
                v = inflater.inflate(R.layout.row, null) ;
            }

            DeviceInfo info = infoList.get(position) ;
            TextView deviceText = (TextView)v.findViewById(devName) ;
            deviceText.setText(info.getName()) ;

            TextView ch1DataText = (TextView)v.findViewById(R.id.dataValue) ;
            String ch1Str ;
            if(info.getCh1Data() == 0xEEEE)
                ch1Str = "----" ;
            else if(info.getCh1Data() == 0xEEE0)
                ch1Str = "" ;
            else
                ch1Str = String.format("%.1f %s", (double)(info.getCh1Data()-1000)/10.0, "\u2103") ;
            ch1DataText.setText(ch1Str) ;

            if((info.getDataTime() + 3*60 < mScanTime/1000) && info.getCh1Data() != 0xEEE0)
                ch1DataText.setTextColor(Color.rgb(210,210,210)) ;
            else
                ch1DataText.setTextColor(Color.rgb(0,0,0)) ;

            TextView timeText = (TextView)v.findViewById(dataTime) ;
            String dataStr ;
            if(info.getDataTime() != 0)
                dataStr = Sb_GetTimeStr(info.getDataTime(), "MM/dd HH:mm:ss") ;
            else
                dataStr = "" ;
            timeText.setText(dataStr) ;

            // Background
            LinearLayout ll = (LinearLayout)v.findViewById(R.id.list_framelayout) ;
            ll.setBackgroundColor(Color.rgb(255,255,238)) ;

            return v ;
        }

        public BluetoothDevice GetBlueToothDevice(int position) {
            return infoList.get(position).getBluetoothDevice() ;
        }

        public void DeleteAllItem() {
            infoList.clear() ;
        }

        // Class that contains the device name and address
        private class DeviceInfo {
            private long serial ;
            private String name ;
            private String address;
            private BluetoothDevice bltDevice ;
            private int ch1Data ;
            private long dataTime ;

            private DeviceInfo(long serial, String name, String address, BluetoothDevice bltDevice,
                               int ch1Data, long dataTime)
            {
                this.serial = serial ;
                this.name = name ;
                this.address = address ;
                this.bltDevice = bltDevice ;
                this.ch1Data = ch1Data ;
                this.dataTime = dataTime ;
            }

            private long getSerial() {
                return serial ;
            }

            private String getName() {
                return name ;
            }

            private String getAddress() {
                return address ;
            }

            private BluetoothDevice getBluetoothDevice() {
                return bltDevice ;
            }

            private int getCh1Data() {
                return ch1Data ;
            }

            private long getDataTime() {
                return dataTime ;
            }
        }

        public void RefreshList() {
            notifyDataSetChanged() ;
        }

    }
}
