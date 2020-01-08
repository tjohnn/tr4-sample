package com.tandd.tr4blesample;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class GetCurrentActivity extends AppCompatActivity implements Handler.Callback{

    private Handler mMyHandler = new Handler(this) ;
    private BluetoothDevice mBleDevice ;

    private ComBle mComBle ;

    Button mButtonOpen ;
    Button mButtonGetcurrent ;

    // Return Value
    public final int BLECom_Success = 0 ;
    public final int BLECom_Err_9FSoh = -110 ;
    public final int BLECom_Err_9FCmd = -111 ;
    public final int BLECom_Err_9FAck = -112 ;
    public final int BLECom_Err_AllSum = -113 ;
    public final int BLECom_Err_9FSum = -114 ;
    public final int BLECom_Err_Soh = -115 ;
    public final int BLECom_Err_Ack09 = -116 ;
    public final int BLECom_Err_Ack0F = -117 ;
    public final int BLECom_Err_Ack15 = -118 ;
    public final int BLECom_Err_AckUkn = -119 ;
    public final int BLECom_Err_DataSum = -120 ;
    public final int BLECom_Err_Exception = -170 ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_getcurrent);

        mComBle = new ComBle(GetCurrentActivity.this, mMyHandler) ;

        // Gets the Argument
        IntentInterface ii = new IntentInterface() ;
        ii = (IntentInterface) getIntent().getSerializableExtra("IntentInterface") ;
        mBleDevice = ii.GetBLEDevice() ;

        // [OPEN] Button
        mButtonOpen = (Button) findViewById(R.id.btn_open);
        mButtonOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mComBle.ComOpen(mBleDevice) ;
            }
        });

        // [Get Current Readings] Button
        mButtonGetcurrent = (Button) findViewById(R.id.btn_getcurrent);
        mButtonGetcurrent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sb_Send_DataHeader() ;
            }
        });

        // [CLOSE] Button
        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sb_CloseBle() ;
            }
        });

        Sb_Control_Button(0) ;
    }

    // ************************************************************************
    // Event Message Processing
    // ************************************************************************
    @Override
    public boolean handleMessage(Message msg)
    {
        int rtn ;

        if(msg.what == Define.MESSAGE_CONNECT_COMPLETE) {
            rtn = mComBle.ComSendNotification() ;
            if(rtn == BLECom_Success) {
                Sb_Control_Button(1) ;
            }
        }
        else if(msg.what == Define.MESSAGE_SEND_DCMD) {
            rtn = Sb_Send_Data() ;
            if(rtn != BLECom_Success)
                Sb_Dsp_Error("Data Section Transmission Error") ;
        }
        else if(msg.what == Define.MESSAGE_RECEIVE_COMPLETE) {
            // Results of Gathering Current Readings
            Sb_Dsp_Current() ;
        }
        else if(msg.what == Define.MESSAGE_DISCONNECT) {
            // Disconnect
            Sb_Control_Button(0) ;
        }

        return true ;
    }


    // ************************************************************************
    // void Sb_CloseBle()
    // Close BLE
    // Argument  :IN :
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_CloseBle()
    {
        mComBle.ComClose() ;
        Sb_Control_Button(0) ;
    }

    // ************************************************************************
    // void Sb_Send_ReqDataHeader()
    // Sends BLE Data Header (Download Header Request)
    // Argument  :IN :
    //       :OUT:
    //       :RET: 
    // ************************************************************************
    private void Sb_Send_DataHeader()
    {
        byte[] sendData = Sb_Make_ReqData() ;
        int rtn = mComBle.ComSendHeader(sendData.length) ;
        if(rtn != BLECom_Success)
            Sb_Dsp_Error("Data Header Transmission Error") ;
    }

    // ************************************************************************
    // int Sb_Send_Data()
    // Sends the command to get current readings
    // Argument  :IN :
    //       :OUT:
    //       :RET:               : Return value from ComBle  0=Success
    // ************************************************************************
    private int Sb_Send_Data()
    {
        int rtn ;
        int roops ;
        int copyLen ;
        byte [] packet_data = new byte[16] ;
        byte[] sendData = Sb_Make_ReqData() ;

        if(sendData.length % 16 == 0)
            roops = sendData.length / 16 ;
        else
            roops = sendData.length / 16 + 1 ;
        for(int i=0; i<roops; i++){
            if(i == roops-1) // Last Data Block
                copyLen = sendData.length - (16*i) ;
            else
                copyLen = 16 ;

            for(int c=0; c<copyLen; c++)
                packet_data[c] = sendData[i*16+c] ;

            rtn = mComBle.ComSendData16(i, packet_data) ;
            if(rtn != BLECom_Success){
                Sb_Dsp_Error("Data Transmission Error") ;
                return rtn ;
            }

            Sb_Sleep(100) ;
        }

        return BLECom_Success ;
    }

    // ************************************************************************
    // byte[] Sb_Make_ReqData()
    // Creates the Current Readings Command
    // Argument  :IN :
    //       :OUT:
    //       :RET:               // Creates the Current Readings Command
    // ************************************************************************
    private byte[] Sb_Make_ReqData()
    {
        byte cmd = (byte)0x33 ;
        int regCode = 0 ;

        // Creates SOH data
        byte [] soh = new byte[11] ;
        soh[0] = (byte)0x01 ;
        soh[1] = cmd ;
        soh[2] = 0x00 ;
        soh[3] = 0x04 ;
        soh[4] = 0x00 ;

        // Adds the checksum to the SOH command
        short sum = Sb_MakeCheckSum(soh, 9) ;
        soh[9] = (byte)(sum & 0xFF) ;
        soh[10] = (byte)((sum >> 8) & 0xFF) ;

        // Wraps with the 9F command
        // [SOH][9F][0][Len][regCode][SOH Data][Sum][Sum2]
        // [  1][ 1][1][  2][      4][       ?][  2][   2] => 13
        byte [] sendData = new byte[soh.length + 13] ;
        Sb_Make_Data(soh, regCode, sendData) ;

        return sendData ;
    }

    // ************************************************************************
    // int Sb_Make_Data(final byte[] soh, long regCode, byte[] sendData)
    // Creates Transfer Data
    // Argument  :IN : soh           : SOH Command
    //             regCode       : Registration Code
    //       :OUT:  sendData      : Transfer Data
    //       :RET:                : Data Size
    // ************************************************************************
    private int Sb_Make_Data(final byte[] soh, long regCode, byte[] sendData)
    {
        byte [] cmd9F = new byte[soh.length+11] ; // +11 is the 9F command specification
        byte [] data = new byte[cmd9F.length+2] ; // +2 is the sum

        // Creates the 9F command
        cmd9F = Sb_Make_9F(regCode, soh) ;

        // Copies data until the 9F command
        int			i			;
        for(i=0; i<cmd9F.length; i++) {
            data[i] = cmd9F[i] ;
        }

        // Adds the sum
        short sum = Sb_MakeCheckSum(cmd9F, cmd9F.length) ;
        data[i] = (byte)(sum & 0xFF) ;
        data[i+1] = (byte)((sum >> 8) & 0xFF) ;

        // Copies to the buffer
        for(i=0; i<data.length; i++){
            sendData[i] = data[i] ;
        }

        return data.length ;
    }

    // ************************************************************************
    // byte[] Sb_Make_9F(long regCode, final byte[] soh)
    // Creates the 9F Command
    // Argument  :IN : regCode   : Registration Code
    //             soh       : SOH Command
    //       :RET:            : Transfer Data
    // ************************************************************************
    private byte[] Sb_Make_9F(long regCode, final byte[] soh)
    {
        int iTmp = 4+soh.length ;
        short dataLen = (short)iTmp ;
        byte [] cmd9F = new byte[5+iTmp+2];
        int i, p ;

        cmd9F[0] = 0x01 ;
        cmd9F[1] = (byte)0x9F ;
        cmd9F[2] = 0x00 ;
        cmd9F[3] = (byte)(dataLen & 0xFF) ;
        cmd9F[4] = (byte)((dataLen >> 8) & 0xFF) ;

        cmd9F[5] = (byte)(regCode & 0xFF) ;
        cmd9F[6] = (byte)((regCode >> 8) & 0xFF) ;
        cmd9F[7] = (byte)((regCode >> 16) & 0xFF) ;
        cmd9F[8] = (byte)((regCode >> 24) & 0xFF) ;

        p=0 ;
        for(i=0; i<soh.length; i++) {
            p=9+i ;
            cmd9F[p] = soh[i] ;
        }

        short crc = Sb_MakeCRC16(cmd9F, cmd9F.length-2, (short)0) ;
        cmd9F[++p] = (byte)((crc >> 8) & 0xFF) ;
        cmd9F[++p] = (byte)(crc & 0xFF) ;

        return cmd9F ;
    }

    // ************************************************************************
    // int Sb_Get_RecvDataPart(final byte[] rcvData,int size, byte[] data)
    // Checks the Received Data (including 9F)
    // Argument  :IN : rcvData   : Data section in the received data (01 9F 06 ...)
    //             size      : Size of the received data
    //       :OUT:   data      : Data section
    //       :RET:             : Various errors   Success=0
    // ************************************************************************
    private int Sb_Get_RecvDataPart(final byte[] rcvData,int size, byte[] data)
    {
        try {
            if(rcvData[0] != (byte)0x01)
                return BLECom_Err_9FSoh;
            if(rcvData[1] != (byte)0x9F)
                return BLECom_Err_9FCmd;
            if(rcvData[2] != (byte)0x06)
                return BLECom_Err_9FAck ;

            int sohLen = Sb_Conv2ByteToInt(rcvData[3], rcvData[4]) ; // Size of the whole SOH data

            // data = [SOH][0x9F][Ack][Data Length][SOH Data][Sum of 9F][Entire Sum]
            //        [  1][  1 ][  1][       2][   sohLen][      2][        2]

            // Checks Entire Checksum
            int sumAll_Cal = Sb_MakeCheckSum(rcvData, size - 2);
            if(sumAll_Cal < 0)
                sumAll_Cal = 65536 + sumAll_Cal ;
            int sumAll_Data = Sb_Conv2ByteToInt(rcvData[5+sohLen+2], rcvData[5+sohLen+2+1]) ;
            if(sumAll_Cal != sumAll_Data)
                return BLECom_Err_AllSum ;

            // Checks CRC of the 9F command
            int crc9F_Cal = Sb_MakeCRC16(rcvData, size-4, (short)0) ;
            if(crc9F_Cal < 0)
                crc9F_Cal = 65536 + crc9F_Cal ;
            int crc9F_Data = Sb_Conv2ByteToInt(rcvData[5+sohLen+1], rcvData[5+sohLen]) ;
            if(crc9F_Cal != crc9F_Data)
                return BLECom_Err_9FSum ;

            // Clips SOH data
            int i ;
            byte[] sohData = new byte[sohLen] ;
            for(i=0; i<sohLen; i++)
                sohData[i] = rcvData[5+i] ;

            if(sohData[0] != (byte)0x01)
                return BLECom_Err_Soh ;

            // Checks status
            if(sohData[2] == (byte)0x06)
                ; // OK
            else if(sohData[2] == (byte)0x09)
                return BLECom_Err_Ack09 ;
            else if(sohData[2] == (byte)0x0F)
                return BLECom_Err_Ack0F ;
            else if(sohData[2] == (byte)0x15)
                return BLECom_Err_Ack15 ;
            else
                return BLECom_Err_AckUkn ;

            int dataLen =  Sb_Conv2ByteToInt(sohData[3], sohData[4]) ; // Size of the data section of SOH data

            // Checks checksum of SOH data
            int sumSoh_Cal = Sb_MakeCheckSum(sohData, sohLen - 2) ;
            if(sumSoh_Cal < 0)
                sumSoh_Cal = 65536 + sumSoh_Cal ;
            int sumSoh_Data = Sb_Conv2ByteToInt(sohData[5+dataLen], sohData[5+dataLen+1]) ;
            if(sumSoh_Cal != sumSoh_Data)
                return BLECom_Err_DataSum ;

            // Copies data section
            for(i=0; i<dataLen; i++) {
                data[i] = sohData[5+i] ;
            }
        }
        catch (Exception e) {
            return BLECom_Err_Exception ;
        }

        return BLECom_Success ;
    }


    // ************************************************************************
    // Creates a 2-byte checksum
    // ************************************************************************
    private short Sb_MakeCheckSum(final byte[] data, int size)
    {
        int iSum = 0 ;
        int iTmp ;

        for(int i=0; i<size; i++) {
            iTmp = data[i] & 0xFF ;
            iSum += iTmp ;
        }

        return (short)iSum ;
    }

    // ************************************************************************
    // Creates CRC16
    // ************************************************************************
    private int crc_table[] = {
            0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50A5, 0x60C6, 0x70E7,
            0x8108, 0x9129, 0xA14A, 0xB16B, 0xC18C, 0xD1AD, 0xE1CE, 0xF1EF,
            0x1231, 0x0210, 0x3273, 0x2252, 0x52B5, 0x4294, 0x72F7, 0x62D6,
            0x9339, 0x8318, 0xB37B, 0xA35A, 0xD3BD, 0xC39C, 0xF3FF, 0xE3DE,
            0x2462, 0x3443, 0x0420, 0x1401, 0x64E6, 0x74C7, 0x44A4, 0x5485,
            0xA56A, 0xB54B, 0x8528, 0x9509, 0xE5EE, 0xF5CF, 0xC5AC, 0xD58D,
            0x3653, 0x2672, 0x1611, 0x0630, 0x76D7, 0x66F6, 0x5695, 0x46B4,
            0xB75B, 0xA77A, 0x9719, 0x8738, 0xF7DF, 0xE7FE, 0xD79D, 0xC7BC,
            0x48C4, 0x58E5, 0x6886, 0x78A7, 0x0840, 0x1861, 0x2802, 0x3823,
            0xC9CC, 0xD9ED, 0xE98E, 0xF9AF, 0x8948, 0x9969, 0xA90A, 0xB92B,
            0x5AF5, 0x4AD4, 0x7AB7, 0x6A96, 0x1A71, 0x0A50, 0x3A33, 0x2A12,
            0xDBFD, 0xCBDC, 0xFBBF, 0xEB9E, 0x9B79, 0x8B58, 0xBB3B, 0xAB1A,
            0x6CA6, 0x7C87, 0x4CE4, 0x5CC5, 0x2C22, 0x3C03, 0x0C60, 0x1C41,
            0xEDAE, 0xFD8F, 0xCDEC, 0xDDCD, 0xAD2A, 0xBD0B, 0x8D68, 0x9D49,
            0x7E97, 0x6EB6, 0x5ED5, 0x4EF4, 0x3E13, 0x2E32, 0x1E51, 0x0E70,
            0xFF9F, 0xEFBE, 0xDFDD, 0xCFFC, 0xBF1B, 0xAF3A, 0x9F59, 0x8F78,
            0x9188, 0x81A9, 0xB1CA, 0xA1EB, 0xD10C, 0xC12D, 0xF14E, 0xE16F,
            0x1080, 0x00A1, 0x30C2, 0x20E3, 0x5004, 0x4025, 0x7046, 0x6067,
            0x83B9, 0x9398, 0xA3FB, 0xB3DA, 0xC33D, 0xD31C, 0xE37F, 0xF35E,
            0x02B1, 0x1290, 0x22F3, 0x32D2, 0x4235, 0x5214, 0x6277, 0x7256,
            0xB5EA, 0xA5CB, 0x95A8, 0x8589, 0xF56E, 0xE54F, 0xD52C, 0xC50D,
            0x34E2, 0x24C3, 0x14A0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
            0xA7DB, 0xB7FA, 0x8799, 0x97B8, 0xE75F, 0xF77E, 0xC71D, 0xD73C,
            0x26D3, 0x36F2, 0x0691, 0x16B0, 0x6657, 0x7676, 0x4615, 0x5634,
            0xD94C, 0xC96D, 0xF90E, 0xE92F, 0x99C8, 0x89E9, 0xB98A, 0xA9AB,
            0x5844, 0x4865, 0x7806, 0x6827, 0x18C0, 0x08E1, 0x3882, 0x28A3,
            0xCB7D, 0xDB5C, 0xEB3F, 0xFB1E, 0x8BF9, 0x9BD8, 0xABBB, 0xBB9A,
            0x4A75, 0x5A54, 0x6A37, 0x7A16, 0x0AF1, 0x1AD0, 0x2AB3, 0x3A92,
            0xFD2E, 0xED0F, 0xDD6C, 0xCD4D, 0xBDAA, 0xAD8B, 0x9DE8, 0x8DC9,
            0x7C26, 0x6C07, 0x5C64, 0x4C45, 0x3CA2, 0x2C83, 0x1CE0, 0x0CC1,
            0xEF1F, 0xFF3E, 0xCF5D, 0xDF7C, 0xAF9B, 0xBFBA, 0x8FD9, 0x9FF8,
            0x6E17, 0x7E36, 0x4E55, 0x5E74, 0x2E93, 0x3EB2, 0x0ED1, 0x1EF0
    } ;

    private short crc_1byte(Byte val, short crc)
    {
        short sVal = val ;
        sVal &= 0xFF ;
        short index = (short) ((crc >> 8) ^ sVal) ;
        index &= 0xFF ;
        return (short)((crc << 8) ^ (short)crc_table[index]) ;
    }

    public short Sb_MakeCRC16(final byte[] buff, int size, short defCrc)
    {
        short crc = defCrc ;
        int pos ;

        for (pos = 0; pos != size; pos++) {
            crc = crc_1byte(buff[pos], crc) ;
        }

        return crc ;
    }

    // ************************************************************************
    // void Sb_Dsp_Current()
    // Displays Current Readings
    // Argument  :IN :
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Dsp_Current()
    {
        String resultStr ;
        byte[] rcvData = mComBle.ComRecvData() ;
        byte[] dataPart = new byte[32] ;
        int rtn = Sb_Get_RecvDataPart(rcvData, rcvData.length, dataPart) ;
        if(rtn == BLECom_Success) {
            int val = Sb_Conv2ByteToSigned(dataPart[0], dataPart[1]) ;
            if(val != 0xEEEE)
                resultStr = String.format("%.1f %s", ((short)val-1000.0)/10.0, "\u2103") ;
            else
                resultStr = "Invalid data" ;
        }
        else {
            resultStr = String.format("Received data error(%d)", rtn) ;
        }

        new AlertDialog.Builder(GetCurrentActivity.this)
                .setTitle("")
                .setMessage(resultStr)
                .setPositiveButton("OK", null)
                .show() ;
    }

    // ************************************************************************
    // void Sb_Dsp_Error(String message)
    // Displays Error
    // Argument  :IN :  message  : Error Message
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Dsp_Error(String message)
    {
        new AlertDialog.Builder(GetCurrentActivity.this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show() ;
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
    // int Sb_Conv2ByteToSigned(byte b1, byte b2)
    // Converts 2-byte signed little endian to int
    // Argument  :IN :  b1      : 1st byte
    //              b2      : 2nd byte
    //       :OUT:
    //       :RET:           : Value after conversion
    // ************************************************************************
    private int Sb_Conv2ByteToSigned(byte b1, byte b2)
    {
        int c1, c2 ;

        c1 = b1 ;
        c2 = b2 ;

        c1 = c1 & 0xFF ;
        c2 = c2 & 0xFF ;

        short val = (short)(c1 + (c2 << 8)) ;

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
    // void Sb_Control_Button(int type)
    // Button Control
    // Argument  :IN :   type    : 0=OPEN Enabled, Getting Current Readings Disabled
    //                         1=OPEN Disabled, Getting Current Readings Enabled
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Control_Button(int type)
    {
        if(type == 0) {
            mButtonOpen.setEnabled(true) ;
            mButtonGetcurrent.setEnabled(false) ;
        }
        else if(type == 1) {
            mButtonOpen.setEnabled(false) ;
            mButtonGetcurrent.setEnabled(true) ;
        }
        else {
            mButtonOpen.setEnabled(false) ;
            mButtonGetcurrent.setEnabled(false) ;
        }
    }

}
