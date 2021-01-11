package com.verifone.sharewifi;

import android.util.Log;

import static android.content.ContentValues.TAG;

public class util {

    /**
     * 获取wifi的有效信息生成wifeinfo对象
     */
    public static WifiInfo getWifonifo(String strResult){
        String passwordTemp = strResult.substring(strResult
                .indexOf("P:"));
        String password = passwordTemp.substring(2,
                passwordTemp.indexOf(";"));
        String netWorkTypeTemp = strResult.substring(strResult
                .indexOf("T:"));
        String netWorkType = netWorkTypeTemp.substring(2,
                netWorkTypeTemp.indexOf(";"));
        String netWorkNameTemp = strResult.substring(strResult
                .indexOf("S:"));
        String netWorkName = netWorkNameTemp.substring(2,
                netWorkNameTemp.indexOf(";"));




        if (password.length()>9 && password.startsWith("\"") && password.endsWith("\"") ){
            password = password.substring(1);
            password = password.substring(0, password.length()-1);
            Log.d(TAG,"wifeinfo : "+password);

        }


        return new WifiInfo(netWorkName,password);
    }
}
