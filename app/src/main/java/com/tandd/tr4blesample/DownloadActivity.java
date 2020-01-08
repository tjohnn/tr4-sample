package com.tandd.tr4blesample;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DownloadActivity extends AppCompatActivity implements Handler.Callback{

    private Handler mMyHandler = new Handler(this) ;
    private BluetoothDevice mBleDevice ;

    private ComBle mComBle ;
    private int mStep ;        // 0:Standby 1:Download Header Request 2:Download Data
    private int mLastDataNo ;  // Data No.
    private long mDownloadStartTime ; // Time when downloading began
    private int mDownloadTotalBlock ;
    private int mDownloadBlock ;
    private byte[] mRecTable = new byte[64] ;
    private byte[] mDownloadData ;
    private int mDownloadRetry ;


    Button mButtonOpen ;
    Button mButtonDownload ;

    private final int Step_ReqHead = 1 ;
    private final int Step_Downloading = 2 ;

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
    public final int BLE4ComLib_Err_BlockNo = -122 ;
    public final int BLECom_Err_Exception = -170 ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloaddata);

        mComBle = new ComBle(DownloadActivity.this, mMyHandler) ;

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

        // [Download Data] Button
        mButtonDownload = (Button) findViewById(R.id.btn_download);
        mButtonDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView tv = (TextView)findViewById(R.id.str_status) ;
                tv.setText("") ;
                mStep = 0 ;

                // Downloads new data since the last download
                if(mLastDataNo != 0)
                    mLastDataNo ++ ;

                Sb_Send_ReqDataHeader() ;
            }
        });

        // [CLOSE] Button
        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Sb_CloseBle() ;
            }
        });

        mLastDataNo = 0 ; // Downloads all data first

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
            if(mStep == Step_ReqHead) {
                // Download Header Request
                rtn = Sb_Send_ReqHead() ;
                if(rtn != BLECom_Success){
                    mStep = 0 ;
                }
            }
            else if(mStep == Step_Downloading) {
                // Download Command
                rtn = Sb_Send_DownloadCmd(0) ;
                if(rtn != BLECom_Success){
                    mStep = 0 ;
                }
            }
        }
        else if(msg.what == Define.MESSAGE_RECEIVE_COMPLETE) {
            if(mStep == Step_ReqHead) {
                // Receives a response to the Download Header Request
                rtn = Sb_Recv_ReqHead() ;
                if(rtn < 0){
                    mStep = 0 ;
                }
                else {
                    // Starts Downloading Data
                    mDownloadRetry = 0 ;
                    mDownloadStartTime = new Date().getTime()/1000 ;
                    Sb_Send_DownloadDataHeader(mDownloadRetry) ;
                }
            }
            else if(mStep == Step_Downloading) {
                // Downloading in Progress
                rtn = Sb_Recv_DownloadData() ;
                if(rtn < 0){
                    mStep = 0 ;
                }
            }
        }
        else if(msg.what == Define.MESSAGE_DISCONNECT) {
            //
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
    private void Sb_Send_ReqDataHeader()
    {
        byte[] sendData = Sb_Make_ReqHead() ;
        int rtn = mComBle.ComSendHeader(sendData.length) ;
        if(rtn != BLECom_Success) {
            Sb_Dsp_Error("Data Header (Download Header Request) Transmission Error") ;
            mStep = 0 ;
        }
        else{
            mStep = Step_ReqHead ;
        }
    }

    // ************************************************************************
    // int Sb_Send_ReqHead()
    // Sends the Download Header Request Command
    // Argument  :IN :
    //       :OUT:
    //       :RET:               : Returns value from ComBle  0=Success
    // ************************************************************************
    private int Sb_Send_ReqHead()
    {
        int rtn ;
        int roops ;
        int copyLen ;
        byte [] packet_data = new byte[16] ;
        byte[] sendData = Sb_Make_ReqHead() ;

        if(sendData.length % 16 == 0)
            roops = sendData.length / 16 ;
        else
            roops = sendData.length / 16 + 1 ;
        for(int i=0; i<roops; i++) {
            if(i == roops-1) // Last Data Block
                copyLen = sendData.length - (16*i) ;
            else
                copyLen = 16 ;

            for(int c=0; c<copyLen; c++)
                packet_data[c] = sendData[i*16+c] ;

            rtn = mComBle.ComSendData16(i, packet_data) ;
            if(rtn != BLECom_Success) {
                Sb_Dsp_Error("Send Data (Download Header Request) error") ;
                return rtn ;
            }

            Sb_Sleep(100) ;
        }

        return BLECom_Success ;
    }

    // ************************************************************************
    // byte[] Sb_Make_ReqHead()
    // Issues the Download Header Request Command
    // Argument  :IN :
    //       :OUT:
    //       :RET:               : Download Header Request command data
    // ************************************************************************
    byte[] Sb_Make_ReqHead()
    {
        byte cmd = (byte)0x49 ; // Specifies by Data No. in this sample
        int regCode = 0 ;

        // Creates SOH data
        byte [] soh = new byte[11] ;
        soh[0] = (byte)0x01 ;
        soh[1] = cmd ;
        soh[2] = 0x00 ;
        soh[3] = 0x04 ;
        soh[4] = 0x00 ;
        soh[5] = (byte)(mLastDataNo & 0xFF) ;
        soh[6] = (byte)((mLastDataNo >> 8) & 0xFF) ;
        soh[7] = (byte)((mLastDataNo >> 16) & 0xFF) ;
        soh[8] = (byte)((mLastDataNo >> 24) & 0xFF) ;

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
    // int Sb_Recv_ReqHead()
    // Receives the Download Header Request Command
    // Argument  :IN :
    //       :OUT:
    //       :RET:               : Returns value from the Sb_Get_RecvDataPart function
    // ************************************************************************
    private int Sb_Recv_ReqHead()
    {
        byte[] rcvData = mComBle.ComRecvData() ;
        int rtn = Sb_Get_RecvDataPart(rcvData, rcvData.length, mRecTable) ;
        if(rtn > 0) {
            int downloadByte = Sb_Conv2ByteToInt(mRecTable[30], mRecTable[31]) ;
            mDownloadData = new byte[downloadByte] ;
            mDownloadBlock = 0 ;

            // Calculates the number of blocks to be downloaded
            int blockSize = 1024 ;
            if((downloadByte % blockSize) == 0)
                mDownloadTotalBlock  = downloadByte / blockSize ;
            else
                mDownloadTotalBlock = downloadByte / blockSize + 1 ;
        }
        else {
            String dspMsg = String.format("Error response (%d) to the Download Header Request command", rtn) ;
            Sb_Dsp_Error(dspMsg) ;
        }

        return rtn ;
    }

    // ************************************************************************
    // void Sb_Send_DownloadDataHeader(int retry)
    // Sends the BLE Data Header (Download Request)
    // Argument  :IN :  retry    : Other than "0" is a Resend request
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Send_DownloadDataHeader(int retry)
    {
        final int RetryNum = 3 ;
        int rtn = 0 ;
        for(int r=0; r<RetryNum; r++) {
            byte[] sendData = Sb_Make_DownloadCmd(retry) ;
            rtn = mComBle.ComSendHeader(sendData.length) ;
            if(rtn == BLECom_Success) {
                mStep = Step_Downloading ;
                break ;
            }

            Sb_Sleep(100) ;
        }

        if(rtn != BLECom_Success)
        {
            Sb_Dsp_Error("Data Header (Download Request) Transmission Error") ;
            mStep = 0 ;
        }
    }

    // ************************************************************************
    // int Sb_Send_DownloadCmd(int retry)
    // Sends the Download Command
    // Argument  :IN :  retry    : Other than "0" is a Resend request
    //       :OUT:
    //       :RET:            : Return value from ComBle  0=Success
    // ************************************************************************
    int Sb_Send_DownloadCmd(int retry)
    {
        int rtn = 0 ;
        int roops ;
        int copyLen ;
        byte[] sendData = Sb_Make_DownloadCmd(retry) ;
        final int RetryNum = 3 ;

        for(int r=0; r<RetryNum; r++) {
            if(sendData.length % 16 == 0)
                roops = sendData.length / 16 ;
            else
                roops = sendData.length / 16 + 1 ;
            for(int i=0; i<roops; i++) {
                byte [] packet_data = new byte[16] ;
                if(i == roops-1) // 
                    copyLen = sendData.length - (16*i) ;
                else
                    copyLen = 16 ;

                for(int c=0; c<copyLen; c++)
                    packet_data[c] = sendData[i*16+c] ;

                rtn = mComBle.ComSendData16(i, packet_data) ;
                if(rtn != BLECom_Success)
                    break ;

                Sb_Sleep(100) ;
            }

            if(rtn == BLECom_Success)
                return BLECom_Success ;

            // Retries
            Log.d("my", "Retries sending the Download command") ;
            Sb_Sleep(100) ;
        }

        if(rtn != BLECom_Success)
            Sb_Dsp_Error("Send Data (Download) Error") ;

        return rtn ;
    }

    // ************************************************************************
    // byte[] Sb_Make_DownloadCmd(int retry)
    // Issues the Download Command
    // Argument  :IN :  retry    : Other than "0" is a Resend request
    //       :OUT:
    //       :RET:            : Download Header Request command data
    // ************************************************************************
    private byte[] Sb_Make_DownloadCmd(int retry)
    {
        byte cmd = (byte)0x41 ;
        int regCode = 0 ;
        int blockSize = 0 ;

        byte subCmd = 0x00 ;
        if(retry != 0)
            subCmd = 0x1F ;

        // Creates SOH data
        byte [] soh = new byte[11] ;
        soh[0] = (byte)0x01 ;
        soh[1] = cmd ;
        soh[2] = subCmd ;
        soh[3] = 0x04 ;
        soh[4] = 0x00 ;
        soh[5] = (byte)(blockSize & 0xFF) ;
        soh[6] = (byte)((blockSize >> 8) & 0xFF) ;
        soh[7] = 0x00 ;
        soh[8] = 0x00 ;

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
    // int Sb_Recv_DownloadData()
    // Gets the Downloaded Data
    // Argument  :IN :
    //       :OUT:
    //       :RET:               : Returns value from the Sb_Get_RecvDataPart function
    // ************************************************************************
    private int Sb_Recv_DownloadData()
    {
        byte[] rcvData = mComBle.ComRecvData() ;
        byte[] dataPart = new byte[1024+50] ;
        TextView tv = (TextView)findViewById(R.id.str_status) ;

        int rtn = Sb_Get_RecvDataPart(rcvData, rcvData.length, dataPart) ;
        if(rtn > 0) {

            int dataPartSize = Sb_Get_DataSize_fromRecvData(rcvData) ;

            for(int i=0; i<dataPartSize; i++)
                mDownloadData[mDownloadBlock*1024 + i] = dataPart[i] ; // Copy of data

            mDownloadBlock = rcvData[7] ;

            // Downloading Status
            String dspStatus = String.format("Download in progress(%d/%d)", mDownloadBlock, mDownloadTotalBlock) ;
            tv.setText(dspStatus) ;

            if(mDownloadBlock < mDownloadTotalBlock) {
                // Download not completed
                mDownloadRetry = 0 ;
                Sb_Send_DownloadDataHeader(mDownloadRetry) ; // Requests next data block
            }
            else {
                // Download completed
                Sb_Convert_DownloadData() ;

                new AlertDialog.Builder(DownloadActivity.this)
                        .setTitle("")
                        .setMessage("Download completed")
                        .setPositiveButton("OK", null)
                        .show() ;
            }
        }
        else {
            if(mDownloadRetry > 2) {
                // Retry up to two times here
                String dspMsg = String.format("Error response (%d) to the Download request", rtn) ;
                Sb_Dsp_Error(dspMsg) ;
            }
            else {
                // Retries
                mDownloadRetry++ ;
                Sb_Send_DownloadDataHeader(mDownloadRetry) ; // Requests re-send of data block

                String log = String.format("Resends request(%d/%d)", mDownloadBlock, mDownloadBlock) ;
                Log.d("my", log) ;
            }
        }

        return rtn ;
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
        int i ;
        for(i=0; i<cmd9F.length; i++){
            data[i] = cmd9F[i] ;
        }

        // Adds the sum
        short sum = Sb_MakeCheckSum(cmd9F, cmd9F.length) ;
        data[i] = (byte)(sum & 0xFF) ;
        data[i+1] = (byte)((sum >> 8) & 0xFF) ;

        // Copies to the buffer to be returned
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
        for(i=0; i<soh.length; i++){
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
    // Extracts SOH Data Section from Received Data (including 9F)
    // Argument  :IN : rcvData   : Data section in the received data (01 9F 06 ...)
    //             size      : Size of the received data
    //       :OUT:  data      : Extracted SOH data section
    //       :RET:            : Various errors   Success = size of the extracted SOH data section
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

            // Extracts SOH Data
            int i ;
            byte[] sohData = new byte[sohLen] ;
            for(i=0; i<sohLen; i++)
                sohData[i] = rcvData[5+i] ;

            if(sohData[0] != (byte)0x01)
                return BLECom_Err_Soh ;

            // Checks the status for the download request.
            // Checks the block number for the data download.
            if(sohData[1] != 0x41)
            {
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
            }
            else {
                int blockNo = sohData[2] ;
                if(blockNo != mDownloadBlock + 1)
                    return BLE4ComLib_Err_BlockNo ;
            }

            int dataLen =  Sb_Conv2ByteToInt(sohData[3], sohData[4]) ; // Size of the data section of SOH data

            // Checks checksum of SOH data
            int sumSoh_Cal = Sb_MakeCheckSum(sohData, sohLen - 2) ;
            if(sumSoh_Cal < 0)
                sumSoh_Cal = 65536 + sumSoh_Cal ;
            int sumSoh_Data = Sb_Conv2ByteToInt(sohData[5+dataLen], sohData[5+dataLen+1]) ;
            if(sumSoh_Cal != sumSoh_Data)
                return BLECom_Err_DataSum ;

            // Copies the data section
            for(i=0; i<dataLen; i++){
                data[i] = sohData[5+i] ;
            }

            return sohData.length ;
        }
        catch (Exception e) {
            return BLECom_Err_Exception ;
        }
    }

    // ************************************************************************
    // int Sb_Get_DataSize_fromRecvData(final byte[] rcvData)
    // Gets the size of data section of SOH data from received data (including 9F command)
    // Argument  :IN : rcvData   : Data section in the received data (01 9F 06 ...)
    //       :OUT:
    //       :RET:            : Size of the data section
    // ************************************************************************
    private int Sb_Get_DataSize_fromRecvData(final byte[] rcvData)
    {

        int sohLen = Sb_Conv2ByteToInt(rcvData[3], rcvData[4]) ; // Size of the whole SOH data

        // data = [SOH][0x9F][Ack][Data Length][SOH Data][Sum of 9F][Entire Sum]
        //        [  1][  1 ][  1][       2][   sohLen][      2][        2]

        // Clips SOH data
        int i ;
        byte[] sohData = new byte[sohLen] ;
        for(i=0; i<sohLen; i++)
            sohData[i] = rcvData[5+i] ;

        int dLen =  Sb_Conv2ByteToInt(sohData[3], sohData[4]) ; // Size of data section of SOH data

        return dLen ;
    }

    // ************************************************************************
    // void Sb_Convert_DownloadData()
    // Converts downloaded data into actual data
    // Argument :IN :
    //       :OUT:
    //      :RET:
    // ************************************************************************
    private void Sb_Convert_DownloadData()
    {
        int intval = Sb_Conv2ByteToInt(mRecTable[0], mRecTable[1]) ;	 // Recording Interval
        int dlByte = Sb_Conv2ByteToInt(mRecTable[30], mRecTable[31])  ;  // Number of bytes downloaded
        int recByte = Sb_Conv2ByteToInt(mRecTable[32], mRecTable[33])  ; // Number of bytes recorded
        int endRemSec = Sb_Conv4ByteToInt(mRecTable[12], mRecTable[13], mRecTable[14], mRecTable[15]) ; // Number of seconds from the most recent recording
        long stTime = Sb_Conv4ByteToInt(mRecTable[2], mRecTable[3], mRecTable[4], mRecTable[5]) ;
        long comStartTime ;         // Time of the first data reading downloaded
        int dataNum = dlByte / 2 ;  // Number of data readings downloaded
        int lastDataNo = Sb_Conv4ByteToInt(mRecTable[26], mRecTable[27],
                                          mRecTable[28], mRecTable[29]) ; // Last recorded data number

        if(mRecTable[10] == 0x02 ||                        // Manual Start
           (mRecTable[11] == 0x00 && recByte == 32000) ||  // Recording mode is "Endless" and total number of bytes recorded is 32000
            stTime == 0)                                    // When the recording start date and time is "0"
           //In the 0x48 command, when a number other than 0 (all data) is specified as the number of bytes to be downloaded
        {
            // Calculates backwards from the system time of the device
            comStartTime = mDownloadStartTime - endRemSec - (intval * (dataNum-1)) ;
        }
        else {
            // Uses the recording start date and time of the received header (mRecTable)
            comStartTime = stTime + (intval * (lastDataNo-1)) - (intval * (dataNum-1)) ;
        }

        mLastDataNo = lastDataNo ;

        // Displays data
        for(int i=0; i<dataNum; i++) {
            int rawaData = Sb_Conv2ByteToSigned(mDownloadData[i*2], mDownloadData[i*2+1]) ;
            String dspLog = String.format("[%d]", i) +
                            Sb_GetTimeStr(comStartTime+i*intval, "yyyy/MM/dd HH:mm:ss") + " " +
                            Sb_ConvRawToStr(rawaData) ;
            Log.d("my", dspLog) ;
        }
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
    // String Sb_ConvRawToStr(int val)
    // Converts temperature and humidity raw data into a character string
    // Argument  :IN :  val    : Temperature Data
    //       :OUT:
    //       :RET:          :
    // ************************************************************************
    public static String Sb_ConvRawToStr(int val)
    {
        String retStr = "" ;

        if(val == (short)0xeeee) {
            retStr = "----" ;
        }
        else {
            double dTmp = (double)((short)val-1000.0)/10.0 ;
            retStr = String.format("%.1f", dTmp) ;
        }

        return retStr ;
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
        new AlertDialog.Builder(DownloadActivity.this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }


    // ************************************************************************
    // int Sb_Conv2ByteToInt(byte b1, byte b2)
    // Converts 2-byte unsigned little endian to int
    // Argument  :IN :  b1      : 1st byte
    //              b2      : 2nd byte
    //       :OUT:
    //       :RET:         : Converted value
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
    //       :RET:           : Converted value
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
    // int Sb_Conv4ByteToInt(byte b1, byte b2, byte b3, byte b4)
    // Converts 4-byte unsigned little endian to int
    // Argument  :IN :  b1      : 1st byte
    //              b2      : 2nd byte
    //              b3      : 3rd byte
    //              b4      : 4th byte
    //       :OUT:
    //       :RET:           : Converted value
    // ************************************************************************
    public int Sb_Conv4ByteToInt(byte b1, byte b2, byte b3, byte b4)
    {
        int c1, c2, c3, c4 ;

        c1 = b1 ;
        c2 = b2 ;
        c3 = b3 ;
        c4 = b4 ;

        c1 = c1 & 0xFF ;
        c2 = c2 & 0xFF ;
        c3 = c3 & 0xFF ;
        c4 = c4 & 0xFF ;

        int val = c1 + (c2 << 8) + (c3 << 16) + (c4 << 24) ;

        return val ;
    }

    // ************************************************************************
    // String Sb_GetTimeStr(long timeData, String format)
    // Returns a formatted time string converted from UNIX time
    // Argument  :IN :  timeData    : UNIX time (in seconds)
    //              format      : Date and Time Format (e.g. "MMM dd,yyyy HH:mm:ss" )
    //       :OUT:
    //       :RET:               : Time String
    // ************************************************************************
    public static String Sb_GetTimeStr(long timeData, String format)
    {
        String retStr = "" ;

        SimpleDateFormat sdf = new SimpleDateFormat(format) ;
        Date orgTime = new Date(timeData*1000) ;
        retStr = sdf.format(orgTime) ;

        return retStr ;
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
    // Argument  :IN :   type    : 0=OPEN Enabled, Download Disabled
    //                         1=OPEN Disabled, Download Enabled
    //       :OUT:
    //       :RET:
    // ************************************************************************
    private void Sb_Control_Button(int type)
    {
        if(type == 0) {
            mButtonOpen.setEnabled(true) ;
            mButtonDownload.setEnabled(false) ;
        }
        else if(type == 1) {
            mButtonOpen.setEnabled(false) ;
            mButtonDownload.setEnabled(true) ;
        }
        else {
            mButtonOpen.setEnabled(false) ;
            mButtonDownload.setEnabled(false) ;
        }
    }

}
