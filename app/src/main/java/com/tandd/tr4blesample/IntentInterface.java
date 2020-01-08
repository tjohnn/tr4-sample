package com.tandd.tr4blesample;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import java.io.Serializable;

public class IntentInterface implements Serializable
{
    private static BluetoothDevice mBLEDevice ;
    private static Context mContext ;

    public void SetBLEDevice(BluetoothDevice bldv)
    {
        mBLEDevice = bldv ;
    }

    public void SetParam(Context context)
    {
        mContext = context ;
    }

    public BluetoothDevice GetBLEDevice()
    {
        return mBLEDevice ;
    }

    public Context GetContext()
    {
        return mContext ;
    }
}
