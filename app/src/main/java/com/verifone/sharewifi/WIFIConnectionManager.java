package com.verifone.sharewifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import android.util.Log;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;

import static com.verifone.sharewifi.DefinedActivity.TAG1;

public class WIFIConnectionManager {

    private static final String TAG = WIFIConnectionManager.class.getName();
    private static WIFIConnectionManager sInstance = null;
    private WifiManager mWifiManager;
    private int networkId;
    private Context ctext;



    public WIFIConnectionManager(Context context) {
        ctext = context;
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public static WIFIConnectionManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (WIFIConnectionManager.class) {
                if (sInstance == null) {
                    sInstance = new WIFIConnectionManager(context);
                }
            }
        }
        return sInstance;
    }


    /**
     * 尝试连接指定wifi
     *i
     * @param wifiinfo     wifi信息
     *
     * @return 是否连接成功
     */
    public boolean connect(@NonNull WifiInfo wifiinfo, boolean isAirPlane) {
        if(isAirPlane){
            return false;
        }
        //判断
        String ssid = wifiinfo.getSsid();
        String password = wifiinfo.getPassword();
        Log.d(TAG1, "connect() called with: ssid = [" + ssid + "], password = [" + password + "]");
        Log.d(TAG1, "connect: wifi opened = " + openWifi());
        while (mWifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED){
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
            }
        }
//        IntentFilter filter = new IntentFilter();
//        filter.setPriority(2147483647);
//        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
//        ctext.registerReceiver(mWifiReceiver, filter);

        boolean isConnected = isConnected(ssid);//当前已连接至指定wifi
        Log.d(TAG, "connect: is already connected = " + isConnected);
        if (isConnected) {
            return true;
        }
        networkId = mWifiManager.addNetwork(newWifiConfig(ssid, password, true));
        Log.d(TAG, "connect: network enabled = " + networkId);
        boolean result = mWifiManager.enableNetwork(networkId, true);
        Log.d(TAG, "connect: network enabled = " + result);
        return result;
    }


    /**
     * 根据wifi名与密码配置 WiFiConfiguration, 每次尝试都会先断开已有连接
     *
     * @param isClient 当前设备是作为客户端,还是作为服务端, 影响SSID和PWD
     * @return
     */
    @NonNull
    private WifiConfiguration newWifiConfig(String ssid, String password, boolean isClient) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        if (isClient) {//作为客户端, 连接服务端wifi热点时要加双引号
            config.SSID = "\"" + ssid + "\"";
            config.preSharedKey = "\"" + password + "\"";
        } else {//作为服务端, 开放wifi热点时不需要加双引号
            config.SSID = ssid;
            config.preSharedKey = password;
        }
        config.hiddenSSID = true;
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.status = WifiConfiguration.Status.ENABLED;
        return config;
    }

    /**
     * @return 热点是否已开启
     */
    public boolean isWifiEnabled() {
        try {
            Method methodIsWifiApEnabled = WifiManager.class.getDeclaredMethod("isWifiApEnabled");
            return (boolean) methodIsWifiApEnabled.invoke(mWifiManager);
        } catch (Exception e) {
            Log.e(TAG, "isWifiEnabled: ", e);
            return false;
        }
    }

    /**
     * 是否已连接指定wifi
     */
    public boolean isConnected(String ssid) {
        android.net.wifi.WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return false;
        }
        switch (wifiInfo.getSupplicantState()) {
            case AUTHENTICATING:
            case ASSOCIATING:
            case ASSOCIATED:
            case FOUR_WAY_HANDSHAKE:
            case GROUP_HANDSHAKE:
            case COMPLETED:
                return wifiInfo.getSSID().replace("\"", "").equals(ssid);
            default:
                return false;
        }
    }

    /**
     * 打开WiFi
     * @return
     */
    public boolean openWifi() {
        boolean opened = true;
        if (!mWifiManager.isWifiEnabled()) {
            opened = mWifiManager.setWifiEnabled(true);
        }
        return opened;
    }

    /**
     * 关闭wifi
     * @return
     */
    public boolean closeWifi() {
        boolean closed = true;
        if (mWifiManager.isWifiEnabled()) {
            closed = mWifiManager.setWifiEnabled(false);
        }
        return closed;
    }

    /**
     * 断开连接
     * @return
     */
    public WIFIConnectionManager disconnect() {
        if (networkId != 0) {
            mWifiManager.disableNetwork(networkId);
        }
        mWifiManager.disconnect();
        return this;
    }



    /**
     * 获取本机的ip地址
     */
    @Nullable
    public String getLocalIp() {
        return convertIp(mWifiManager.getConnectionInfo().getIpAddress());
    }

    private String convertIp(int ipAddress) {
        if (ipAddress == 0) return null;
        return ((ipAddress & 0xff) + "." + (ipAddress >> 8 & 0xff) + "."
                + (ipAddress >> 16 & 0xff) + "." + (ipAddress >> 24 & 0xff));
    }

    public WifiManager getWifiManager() {
        return mWifiManager;
    }


    /**
     * 获取本机的飞行模式状态
     */
    public  boolean getAirplaneMode(){
        int isAirplaneMode = Settings.System.getInt(ctext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) ;
        return (isAirplaneMode == 1)?true:false;
    }

    private BroadcastReceiver mWifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {// 这个监听wifi的打开与关闭，与wifi的连接无关
                    abortBroadcast();
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                    switch (wifiState) {
                        case WifiManager.WIFI_STATE_ENABLED:

                            break;
                    }
                }
            }
        }
    };



}