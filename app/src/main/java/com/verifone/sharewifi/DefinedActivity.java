package com.verifone.sharewifi;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;


import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.verifone.sharewifi.zxing.android.BeepManager;
import com.verifone.sharewifi.zxing.android.CaptureActivityHandler;
import com.verifone.sharewifi.zxing.android.FinishListener;
import com.verifone.sharewifi.zxing.android.InactivityTimer;
import com.verifone.sharewifi.zxing.android.IntentSource;
import com.verifone.sharewifi.zxing.camera.CameraManager;
import com.verifone.sharewifi.zxing.view.ViewfinderView;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;



/**
 * 这个activity打开相机，在后台线程做常规的扫描；它绘制了一个结果view来帮助正确地显示条形码，在扫描的时候显示反馈信息，
 * 然后在扫描成功的时候覆盖扫描结果
 */
public final class DefinedActivity extends Activity implements
        SurfaceHolder.Callback {
    public static final int DEFINED_CODE = 222;

    private static final String TAG = DefinedActivity.class.getSimpleName();
    public static final String TAG1 = "sharewifitest";

    // 相机控制
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private IntentSource source;
    private Collection<BarcodeFormat> decodeFormats;
    private Map<DecodeHintType, ?> decodeHints;
    private String characterSet;
    // 电量控制
    private InactivityTimer inactivityTimer;
    // 声音、震动控制
    private BeepManager beepManager;

    private ImageButton imageButton_back;

    private ImageButton imageButton_switch;

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

//    public NormalDialog normalDialog;

    private static long mLastActionTime;

    Timer mTimer;

    MyTimerTask mTimerTask;

    MediaPlayer player;

    //判断前后置摄像头
    static public int cameraInfo;

    private SurfaceView surfaceView ;

    private SurfaceHolder surfaceHolder;


    public ProgressDialog waitingDialog;

    /**
     * OnCreate中初始化一些辅助类，如InactivityTimer（休眠）、Beep（声音）以及AmbientLight（闪光灯）
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // 保持Activity处于唤醒状态
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.capture);

        hasSurface = false;



        cameraInfo = Camera.CameraInfo.CAMERA_FACING_BACK;

        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);

        imageButton_back = (ImageButton) findViewById(R.id.capture_imageview_back);
        imageButton_back.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }
        });
        imageButton_switch = (ImageButton) findViewById(R.id.camera_switch);
        imageButton_switch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (cameraInfo == Camera.CameraInfo.CAMERA_FACING_BACK){
                    cameraInfo = Camera.CameraInfo.CAMERA_FACING_FRONT;
                }else {
                    cameraInfo = Camera.CameraInfo.CAMERA_FACING_BACK;
                }
                initCamera(surfaceHolder);
            }
        });
        init();

        Log.d(TAG1, "onCreate is excute");
        //开启计时，30秒后关闭app
        startTimer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.requestPermissions(
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE},
                    DEFINED_CODE);
        }else{
            Log.d(TAG1, Integer.toString(Build.VERSION.SDK_INT));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions == null || grantResults == null || grantResults.length < 2 || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (requestCode == DEFINED_CODE) {
            initCamera(surfaceHolder);
            Log.d(TAG1, "DefinedActivity onResume is excute");
        }

    }


    @Override
    protected void onResume() {
        super.onResume();


    }

    private void init(){
        // CameraManager必须在这里初始化，而不是在onCreate()中。
        // 这是必须的，因为当我们第一次进入时需要显示帮助页，我们并不想打开Camera,测量屏幕大小
        // 当扫描框的尺寸不正确时会出现bug
        if (cameraManager == null){
            cameraManager = new CameraManager(getApplication());
        }
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(cameraManager);

        handler = null;

        surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            // activity在paused时但不会stopped,因此surface仍旧存在；
            // surfaceCreated()不会调用，因此在这里初始化camera
            initCamera(surfaceHolder);
            Log.d(TAG1, "onResume is excute");
        } else {
            // 重置callback，等待surfaceCreated()来初始化camera
            surfaceHolder.addCallback(this);
        }

        beepManager.updatePrefs();
        inactivityTimer.onResume();

        source = IntentSource.NONE;
        decodeFormats = null;
        characterSet = null;
        Log.d(TAG1, "onResume is excute");
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!hasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        Log.d(TAG1, "onPause is excute");
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG1, "onStop is excute");
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        stopTimer();
        if (player!=null){
            player.release();
        }

        Log.d(TAG1, "onDestory is excute");
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    /**
     * 扫描成功，处理反馈信息
     *
     * @param rawResult
     * @param barcode
     * @param scaleFactor
     */
    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();

        boolean fromLiveScan = barcode != null;
        if (fromLiveScan) {
            beepManager.playBeepSoundAndVibrate();
//            normalDialog = new NormalDialog(this,handler);
            Log.d(TAG1,rawResult.getText());
            if (!rawResult.getText().startsWith("WIFI:")){
                mediaPlay("invalidcode.mp3");
                Message msg = handler.obtainMessage(R.id.restart_preview);
                handler.sendMessageDelayed(msg,2000);
            }else {
                WifiInfo wifiinfo = util.getWifonifo(rawResult.getText());
                Log.d(TAG1,wifiinfo.toString());
                    //normalDialog.showNormalDialog(wifiinfo,isActivityFocus);
                connect(wifiinfo);
            }



        }
    }



    /**
     * 初始化Camera
     *
     * @param surfaceHolder
     */
    public void initCamera(SurfaceHolder surfaceHolder) {
        Log.d(TAG1,"cameraManager.isOpen() is"+ "2222");
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        Log.d(TAG1,"cameraManager.isOpen() is"+ "11111");

        if (cameraManager.isOpen()) {
            // 如果相机已经打开 则关闭当前相机 重建一个 切换摄像头，，如果不需要切换前置摄像头 则这里直接return
            handler = null;
            cameraManager.closeDriver();
//            return;
        }
        try {
            // 打开Camera硬件设备
            cameraManager.openDriver(surfaceHolder,cameraInfo);
            //有机型出现过前置摄像头图像反转，手动将图像转正
            if(cameraInfo == Camera.CameraInfo.CAMERA_FACING_BACK){
                cameraManager.setCameraDisplayOrientation(this);
            }
            // 创建一个handler来打开预览，并抛出一个运行时异常
            if (handler == null) {
                Log.d(TAG1,"new handler is excute");
                handler = new CaptureActivityHandler(this, decodeFormats,
                        decodeHints, characterSet, cameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    /**
     * 显示底层错误信息并退出应用
     */
    private void displayFrameworkBugMessageAndExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage(getString(R.string.msg_camera_framework_bug));
        builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
        builder.setOnCancelListener(new FinishListener(this));
        builder.show();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        mLastActionTime = System.currentTimeMillis();
        return super.dispatchTouchEvent(ev);
    }

    private  class MyTimerTask extends TimerTask {

        @Override
        public void run() {
            if (System.currentTimeMillis() - mLastActionTime >30 * 1000) {
                finish();
                // 停止计时任务
                stopTimer();
            }
        }
    }

    // APP开启，开始计时
    protected  void startTimer() {
        mTimer = new Timer();
        mTimerTask = new MyTimerTask();
        // 初始化上次操作时间为登录成功的时间
        mLastActionTime = System.currentTimeMillis();
        // 每过1s检查一次
        mTimer.schedule(mTimerTask, 0, 1000);
        Log.d(TAG1, "start timer");
    }

    // 停止计时任务
    protected  void stopTimer() {
        mTimer.cancel();
        Log.d(TAG1, "cancel timer");
    }


    /**
     * 播放连接提示音
     */
    public void mediaPlay(String fileName){
        AssetManager assetManager;
        assetManager = getResources().getAssets();
        player = new MediaPlayer();
        try {
            AssetFileDescriptor fileDescriptor = assetManager.openFd(fileName);
            player.setDataSource(fileDescriptor.getFileDescriptor(), fileDescriptor.getStartOffset(), fileDescriptor.getLength());
            player.prepare();
            player.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 连接Wi-Fi
     */
    private void connect(final WifiInfo wifiInfo){
        cameraManager.stopPreview();
        waitingDialog = new ProgressDialog(this);
        waitingDialog.setTitle("正在连接");
        waitingDialog.setMessage("正在连接名称为： " + wifiInfo.getSsid() + "  的Wi-Fi");
        waitingDialog.setIndeterminate(true);
        waitingDialog.setCancelable(false);
        waitingDialog.show();
        final WIFIConnectionManager wifiConnectionManager = new WIFIConnectionManager(this);
        new Thread(new Runnable() {
            @Override
            public void run() {

                if (!wifiConnectionManager.getAirplaneMode()){
                    mediaPlay("connect.mp3");
                    wifiConnectionManager.openWifi();
                    wifiConnectionManager.connect(wifiInfo,wifiConnectionManager.getAirplaneMode());
                    sleep(2000);
                    if(wifiConnectionManager.isConnected(wifiInfo.getSsid())){
                        mediaPlay("success.mp3");
                        sleep(2000);
                        finish();
//                        Log.d(TAG1, "d is send");
                    }else {
                        mediaPlay("fail.mp3");
                        sleep(2000);
                        sendMessage(R.id.restart_preview);
//                        Log.d(TAG1, "b is send");
                    }
                }else {
                    mediaPlay("airplane.mp3");
                    sleep(3000);
                    sendMessage(R.id.restart_preview);

                }

                waitingDialog.dismiss();
            }

            public void sleep(long sleepTime){
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void sendMessage(Integer what){
        Message msg = handler.obtainMessage(what);
        handler.sendMessage(msg);
    }



}
