package com.tandd.tr4blesample;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Message;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;


public class ComBle
{
    private Context mContext ;
    private Handler mHandler ;

    public int mStep ;   // Processing steps
    public int mStatus ; // Processing results

    private int mRecvTotal ;         // Total size of [KA1][KA3]bytes[KA4] to be received[KA5]
    private int mRecvTotalBlock ;   // Number of blocks to be received
    private byte[] mRecvData ;
    public byte[][] mRecvPacket ;
    private int mRecvCurBlock ;     // Block Number

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic ;

    private static final UUID TDUD_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca42");
    private static final UUID SEND_PACKET_COMMAND_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca42");
    private static final UUID DISCOVERY_DATA_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca42");
    private static final UUID SEND_PACKET_RESPONSE_UUID = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca42");
    private static final UUID RECEIVE_PACKET_COMMAND_UUID = UUID.fromString("6e400005-b5a3-f393-e0a9-e50e24dcca42");
    private static final UUID RECEIVE_PACKET_DATA_UUID = UUID.fromString("6e400006-b5a3-f393-e0a9-e50e24dcca42");
    private static final UUID RECEIVE_PACKET_RESPONSE_UUID = UUID.fromString("6e400007-b5a3-f393-e0a9-e50e24dcca42");

    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public final int BLECom_Step_IDLE = 0 ;              // Standby
    public final int BLECom_Step_Connect = 1 ;           // Connected to TR4 successfully
    public final int BLECom_Step_SetCharNotif = 2 ;      // Enables Notifications
    public final int BLECom_Step_SendCmd = 3 ;           // Sends a command to TR4
    public final int BLECom_Step_RecvData = 4 ;          // Receives data from TR4
    public final int BLECom_Step_RecvDataSuccess = 5 ;   // Data reception from TR4 completed

    // Return Value
    public final int BLECom_Success = 0 ;
    public final int BLECom_Err_NotFound_Dev = -8 ; // TR4 not found upon Opening BLE (Error 133)
    public final int BLECom_Err_Connect_Disc = -9 ; // Disconnected shortly after connecting
    public final int BLECom_Err_NotFoundService = -11 ;
    public final int BLECom_Err_NotFoundChar = -12 ;
    public final int BLECom_Err_SetCharNotif = -13 ;
    public final int BLECom_Err_TDUD_SERVICE = -14 ;
    public final int BLECom_Err_WriteDescriptor = -15 ; // Notification Error
    public final int BLECom_Err_WriteCharacteristic = -16 ;
    public final int BLECom_Err_RecvCmdChar = -18 ;
    public final int BLECom_Err_NULLGATT = -19 ;
    public final int BLECom_Err_Send = -21 ;
    public final int BLECom_Err_Exception1 = -50 ;
    public final int BLECom_Err_Exception2 = -51 ;
    public final int BLECom_Err_Exception3 = -52 ;

    public final int BLECom_Process = 1 ; // In process
    public final int BLECom_Disconnect = 2 ; // Disconnected


    public ComBle(final Context context, final Handler handler)
    {
        mContext = context ;
        mHandler = handler ;
    }

    // ************************************************************************
    // void ComOpen(final BluetoothDevice btdv)
    // Open
    // Argument  :IN : btdv    : BluetoothDevice
    //       :OUT:
    //       :RET:
    // ************************************************************************
    public void ComOpen(final BluetoothDevice btdv)
    {
        mBluetoothGatt = btdv.connectGatt(mContext, false, mBluetoothGattCallback);
        refreshDeviceCache(mBluetoothGatt) ;
        mStep = BLECom_Step_IDLE ;
    }

    // ************************************************************************
    // void void ComClose()
    // Close
    // Argument  :IN :
    //       :OUT:
    //       :RET:
    // ************************************************************************
    public void ComClose()
    {
        if (mBluetoothGatt != null){
            mBluetoothGatt.close();
            mBluetoothGatt.disconnect();
            mBluetoothGatt = null;
        }
    }

    // ************************************************************************
    // int ComSendNotification()
    // Sends a Notification
    // Argument  :IN :
    //       :OUT:
    //       :RET:        : Return values (Negative number is "error", 0 is "success")
    // ************************************************************************
    public int ComSendNotification()
    {
        int rtn = Sb_Send_Ble_Notification() ;

        return rtn ;
    }

    // ************************************************************************
    // int ComSendHeader(int size)
    // Sends Data Header
    // Argument  :IN :  size : Total size of the sent data
    //       :OUT:
    //       :RET:        : Return values (Negative number means "error", 0 is "success")
    // ************************************************************************
    public int ComSendHeader(int size)
    {
        mStatus = BLECom_Process ;

        mStep = BLECom_Step_SendCmd ;
        try {
            // Makes and Sends Command Characteristic
            byte [] packet_head = new byte[20] ;
            short dataSize = (short)size ;
            packet_head[0] = 0x01 ;
            packet_head[1] = 0x01 ;
            packet_head[2] = (byte)(dataSize & 0xFF) ;
            packet_head[3] = (byte)((dataSize >> 8) & 0xFF) ;

            BluetoothGattService service = mBluetoothGatt.getService(TDUD_SERVICE_UUID) ;
            BluetoothGattCharacteristic characteristic_Dcmd = service.getCharacteristic(SEND_PACKET_COMMAND_UUID) ;
            characteristic_Dcmd.setValue(packet_head);
            boolean bRet = mBluetoothGatt.writeCharacteristic(characteristic_Dcmd);
            if(bRet)
                return BLECom_Success ;
            else
                return BLECom_Err_WriteCharacteristic ;
        }
        catch (Exception e) {
            return BLECom_Err_Exception1 ;
        }

    }

    // ************************************************************************
    // int ComSendData16(int block, final byte[] data)
    // Sends 16 byte data
    // Argument  :IN :  block    : Block Number
    //              data     : Transfer Data
    //       :OUT:
    //       :RET:            : Return values (Negative number means "error", 0 is "success")
    // ************************************************************************
    public int ComSendData16(int block, final byte[] data)
    {
        mStatus = BLECom_Process ;

        mStep = BLECom_Step_SendCmd ;
        try {
            // Makes and Sends Data Characteristic
            byte [] packet_data = new byte[20];
            packet_data[0] = (byte)(block & 0xFF) ;
            packet_data[1] = (byte)((block >> 8) & 0xFF) ;
            packet_data[2] = 0x00 ;
            packet_data[3] = 0x00 ;
            for(int i=0; i<data.length; i++)
                packet_data[4+i] = data[i] ;

            mCharacteristic.setValue(packet_data);
            boolean bRet = mBluetoothGatt.writeCharacteristic(mCharacteristic);
            if(!bRet) {
                mStatus = BLECom_Err_Send ;
                return mStatus ;
            }
            else {
                return BLECom_Success ;
            }
        }
        catch (Exception e) {
            return BLECom_Err_Exception1 ;
        }
    }

    // ************************************************************************
    // byte[] ComRecvData()
    // Receives Data
    // Argument  :IN :
    //       :OUT:
    //       :RET:       : Received Data
    // ************************************************************************
    public byte[] ComRecvData()
    {
        byte[] retData = new byte[mRecvData.length] ;
        for(int i=0; i<mRecvData.length; i++)
            retData[i] = mRecvData[i] ;
        return retData ;
    }




    private boolean refreshDeviceCache(BluetoothGatt gatt)
    {
        try {
            BluetoothGatt localBluetoothGatt = gatt ;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]) ;
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])) ;
                return bool ;
            }
        }
        catch (Exception localException) {

        }
        return false;
    }



    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Connected to GATT successfully. Searches for service.
                gatt.discoverServices() ;
                mStep = BLECom_Step_Connect ;
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Disconnected from GATT server.
                // Includes when this application disconnects,
                // when the TR4 is powered OFF or goes out of range.
                if(mStep == BLECom_Step_Connect) {
                    mStatus = BLECom_Err_Connect_Disc ;
                }
                else {
                    if(status == 133) { // TR4 not found upon Opening BLE
                        mStatus = BLECom_Err_NotFound_Dev ;
                    }
                    else {
                        mStatus = BLECom_Disconnect ;
                        Sb_Send_Message(Define.MESSAGE_DISCONNECT, "") ;
                    }
                }

                if(mBluetoothGatt != null) {
                    mBluetoothGatt.close() ;
                    mBluetoothGatt = null ;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(TDUD_SERVICE_UUID) ;
                if (service == null) {
                    // Did not find the Service
                    mStatus = BLECom_Err_NotFoundService ;
                }
                else {
                    // Found the Service
                    mCharacteristic = service.getCharacteristic(DISCOVERY_DATA_UUID) ;
                    if (mCharacteristic == null) {
                        // Did not find the Characteristic
                        mStatus = BLECom_Err_NotFoundChar ;
                    }
                    else {
                        // Found the Characteristic
                        Sb_Send_Message(Define.MESSAGE_CONNECT_COMPLETE, "") ;
                        mStep = BLECom_Step_SetCharNotif ;
                    }
                }
            }
            else {
                mStatus = BLECom_Err_NotFoundService ;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status)
        {
            if(characteristic.getUuid().equals(SEND_PACKET_COMMAND_UUID)) {
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    Sb_Send_Message(Define.MESSAGE_SEND_DCMD, "") ;
                }
                else {
                    // Does not re-send
                }
            }
            else if(characteristic.getUuid().equals(DISCOVERY_DATA_UUID)) {
                if(status == BluetoothGatt.GATT_SUCCESS) {
                }
                else {
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status)
        {

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            byte [] packet = characteristic.getValue() ;
            // Notification of updating the value of the Characteristic

            if (RECEIVE_PACKET_COMMAND_UUID.equals(characteristic.getUuid())) {
                if(packet[0] == 1 && packet[1] == 1) {
                    mStep = BLECom_Step_RecvData ;
                    mRecvTotal = Sb_Conv2ByteToInt(packet[2], packet[3]) ;

                    // �g�[�^���o�C�g���͖{���̃f�[�^�̐��B�i�폜�j
                    // 20-byte data packets are sent via the data characteristic (4 bytes of header + 16 bytes of data).
                    mRecvData = new byte[mRecvTotal] ;
                    if(mRecvTotal % 16 != 0)
                        mRecvTotalBlock = mRecvTotal/16 + 1 ;
                    else
                        mRecvTotalBlock = mRecvTotal/16 ;
                    mRecvCurBlock = 0 ;

                    mRecvPacket = new byte[mRecvTotalBlock][20] ;
                }
                else {
                    mStatus = BLECom_Err_RecvCmdChar ;
                }
            }
            else if (RECEIVE_PACKET_DATA_UUID.equals(characteristic.getUuid())) {

                int bNo = Sb_Conv2ByteToInt(packet[0], packet[1]) ;
                if(mRecvCurBlock > bNo)
                    return ;

                mRecvPacket[bNo] = Arrays.copyOf(packet, 20) ;
                mRecvCurBlock = Sb_Conv2ByteToInt(packet[0], packet[1]) ;

                int i ;
                int copyNum = 16 ;

                if(mRecvCurBlock+1 >= mRecvTotalBlock) {
                    // Received all
                    Sb_Send_Confirm(RECEIVE_PACKET_RESPONSE_UUID) ;

                    for(int b=0; b<mRecvTotalBlock; b++) {
                        int pp = b*16 ;
                        if(b+1 >= mRecvTotalBlock) {
                            if(mRecvTotal % 16 == 0)
                                copyNum = 16 ;
                            else
                                copyNum = mRecvTotal % 16 ;
                        }
                        for(i=0; i<copyNum; i++)
                            mRecvData[pp+i] = mRecvPacket[b][i+4] ;

                        mStep = BLECom_Step_RecvDataSuccess ;
                        mStatus = BLECom_Success ;
                    }

                    Sb_Send_Message(Define.MESSAGE_RECEIVE_COMPLETE, "") ;
                }
            }
            else if (SEND_PACKET_RESPONSE_UUID.equals(characteristic.getUuid())) {
                // Response to transfer data
                // Does not check here
            }

        }
    };

    // ************************************************************************
    // int Sb_Send_Ble_Notification()
    // Sends a Notification
    // Argument  :IN :
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private int Sb_Send_Ble_Notification()
    {
        boolean rtn ;

        if(mBluetoothGatt == null) {
            return BLECom_Err_NULLGATT ;
        }

        try {
            BluetoothGattService service = mBluetoothGatt.getService(TDUD_SERVICE_UUID); // (*1)
            if(service == null)
                return BLECom_Err_TDUD_SERVICE ;

            BluetoothGattCharacteristic Dcfm_Char = service.getCharacteristic(SEND_PACKET_RESPONSE_UUID);
            rtn = mBluetoothGatt.setCharacteristicNotification(Dcfm_Char, true);
            if(rtn){
                BluetoothGattDescriptor descriptor1 = Dcfm_Char.getDescriptor(CCCD);
                descriptor1.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                rtn = mBluetoothGatt.writeDescriptor(descriptor1);
                if(!rtn)
                    return BLECom_Err_WriteDescriptor ;
            }
            else
                return BLECom_Err_SetCharNotif ;
        }
        catch(Exception e) {
            // An exception occurred in (*1)
            return BLECom_Err_Exception2 ;
        }

        Sb_Sleep(100) ;

        try {
            BluetoothGattService service = mBluetoothGatt.getService(TDUD_SERVICE_UUID) ; // (*2)
            if(service == null)
                return BLECom_Err_TDUD_SERVICE ;

            BluetoothGattCharacteristic Ucmd_Char = service.getCharacteristic(RECEIVE_PACKET_COMMAND_UUID) ;
            rtn = mBluetoothGatt.setCharacteristicNotification(Ucmd_Char, true) ;
            if(rtn) {
                BluetoothGattDescriptor descriptor2 = Ucmd_Char.getDescriptor(CCCD) ;
                descriptor2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ;
                rtn = mBluetoothGatt.writeDescriptor(descriptor2) ;
                if(!rtn)
                    return BLECom_Err_WriteDescriptor ;
            }
            else
                return BLECom_Err_SetCharNotif ;
        }
        catch(Exception e) {
            // An exception occurred in (*2)
            return BLECom_Err_Exception2 ;
        }

        Sb_Sleep(100) ;

        try {
            BluetoothGattService service = mBluetoothGatt.getService(TDUD_SERVICE_UUID) ; // (*3)
            if(service == null)
                return BLECom_Err_TDUD_SERVICE ;

            BluetoothGattCharacteristic Udat_Char = service.getCharacteristic(RECEIVE_PACKET_DATA_UUID) ;
            rtn = mBluetoothGatt.setCharacteristicNotification(Udat_Char, true) ;
            if(rtn) {
                BluetoothGattDescriptor descriptor3 = Udat_Char.getDescriptor(CCCD) ;
                descriptor3.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ;
                rtn = mBluetoothGatt.writeDescriptor(descriptor3) ;
                if(!rtn)
                    return BLECom_Err_WriteDescriptor ;
            }
            else
                return BLECom_Err_SetCharNotif ;
        }
        catch(Exception e) {
            // An exception occurred in (*3)
            return BLECom_Err_Exception2 ;
        }

        return BLECom_Success ;
    }

    // ************************************************************************
    // void Sb_Send_Confirm(UUID uuid)
    // Sends response data to TR4
    // Argument  :IN :   uuid    : UUID
    // ************************************************************************
    private void Sb_Send_Confirm(UUID uuid)
    {
        byte[] data = new byte[20];

        data[0] = 0x01 ;
        data[1] = 0x00 ;

        try {
            BluetoothGattService service = mBluetoothGatt.getService(TDUD_SERVICE_UUID);
            BluetoothGattCharacteristic characteristic_cmd = service.getCharacteristic(uuid);
            characteristic_cmd.setValue(data);
            mBluetoothGatt.writeCharacteristic(characteristic_cmd);
        }
        catch (Exception e) {
            mStatus = BLECom_Err_Exception3 ;
        }
    }


    // ************************************************************************
    // int Sb_Conv2ByteToInt(byte b1, byte b2)
    // Converts 2-byte unsigned little endian to int
    // Argument  :IN :  b1      : 1st byte
    //              b2      : 2nd byte
    //       :OUT:
    //       :RET:               : Value after conversion
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
    // void Sb_Sleep(long sTime)
    // Sleep
    // Argument  :IN :  sTime      : ms
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Sleep(long sTime)
    {
        try {
            Thread.sleep(sTime) ;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // ************************************************************************
    // void Sb_Sleep(long sTime, String messageStr)
    // Sleep
    // Argument  :IN :  sTime      : ms
    //              messageStr   : Additional information
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Send_Message(int arg1, String messageStr)
    {
        Message message = new Message() ;
        message.what = arg1 ;
        message.obj = messageStr ;
        mHandler.sendMessage(message) ;
    }
}

