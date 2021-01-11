package com.verifone.sharewifi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import static android.content.ContentValues.TAG;
import static com.verifone.sharewifi.DefinedActivity.TAG1;


public class NormalDialog {

    final AlertDialog.Builder normalDialog;

    final AlertDialog.Builder resultDialog;

    AlertDialog dialog;

    WIFIConnectionManager wifiConnectionManager;

    Handler mHandler;

    Context mContext;


    public NormalDialog(@NonNull Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        this.normalDialog = new AlertDialog.Builder(context);
        this.resultDialog = new AlertDialog.Builder(context);
        wifiConnectionManager = new WIFIConnectionManager(context);

    }

    interface TimerListener{
        void onTick();

    }





    //提示是否连接提示框
    public void showNormalDialog(final WifiInfo wifiinfo, boolean isActivityTop, final TimerListener timerListener){
        /* @setIcon 设置对话框图标
         * @setTitle 设置对话框标题
         * @setMessage 设置对话框消息提示
         * setXXX方法返回Dialog对象，因此可以链式设置属性
         */
        final CountDownTimer timer = new CountDownTimer(5000,1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int theTime = (int) (millisUntilFinished / 1000);
                if (dialog != null) {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("连接(" + theTime + ")");
                    timerListener.onTick();
                }
            }

            @Override
            public void onFinish() {
                if (dialog != null){
                    dialog.dismiss();
                }
//                connect(wifiinfo);
            }
        };
        normalDialog.setTitle("确认连接");
        normalDialog.setMessage("是否连接名称为： " + wifiinfo.getSsid() + "  的Wi-Fi");
        normalDialog.setPositiveButton("连接",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//                        connect(wifiinfo);
                        timer.cancel();
                    }
                });
        normalDialog.setNegativeButton("不连接",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendMessage(R.id.restart_preview);
                        timer.cancel();
                    }
                });
        // 显示

        dialog = normalDialog.create();
        dialog.show();
        timer.start();
    }

    private void connect(final WifiInfo wifiInfo){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!wifiConnectionManager.getAirplaneMode()){
                    wifiConnectionManager.connect(wifiInfo,true);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(wifiConnectionManager.connect(wifiInfo,true) && wifiConnectionManager.isConnected(wifiInfo.getSsid())){
                        sendMessage(R.id.a);
                        Log.d(TAG1, "a is send");
                    }else {
                        sendMessage(R.id.b);
                        Log.d(TAG1, "b is send");
                    }
                }else {
                    sendMessage(R.id.c);
                    Log.d(TAG1, "c is send");
                }
            }
        }).start();
    }


    //连接结果提示框
    public void resultDialog(final String message,boolean isActivityTop){
        long millisInFuture;
        if (isActivityTop){
            millisInFuture = 5000;
            Log.d(TAG1,"millisInFuture = 5000");
        }else {
            millisInFuture = (long) 0;
            Log.d(TAG1,"millisInFuture = 0");
        }
        final CountDownTimer timer = new CountDownTimer(millisInFuture,1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int theTime = (int) (millisUntilFinished / 1000);
                if (dialog != null) {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("确定(" + theTime + ")");
                }
            }

            @Override
            public void onFinish() {
                if (dialog != null){
                    dialog.dismiss();
                }
                if (message.equals(mContext.getString(R.string.connect_success))){
                    sendMessage(R.id.d);
                    Log.d(TAG1, "d is send");
                }else {
                    sendMessage(R.id.restart_preview);
                    Log.d(TAG1, "restart_preview is send");
                }
            }
        };
        resultDialog.setTitle("连接提示");
        resultDialog.setMessage(message);
        resultDialog.setPositiveButton("确定",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (message.equals(mContext.getString(R.string.connect_success))){
                            sendMessage(R.id.d);
                            Log.d(TAG1, "d is send");
                        }else {
                            sendMessage(R.id.restart_preview);
                            Log.d(TAG1, "restart_preview is send");
                        }
                        timer.cancel();
                    }
                });


        // 显示
        dialog = resultDialog.create();
        dialog.show();
        timer.start();

    }

    public void sendMessage(Integer what){
        Message msg = mHandler.obtainMessage(what);
        mHandler.sendMessage(msg);
    }
}
