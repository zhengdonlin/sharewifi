/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.verifone.sharewifi.zxing.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;

import java.io.IOException;

import static com.verifone.sharewifi.DefinedActivity.TAG1;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

	private static final String TAG = CameraManager.class.getSimpleName();

	private static final int MIN_FRAME_WIDTH = 240;
	private static final int MIN_FRAME_HEIGHT = 240;
	private static final int MAX_FRAME_WIDTH = 1200; // = 5/8 * 1920
	private static final int MAX_FRAME_HEIGHT = 675; // = 5/8 * 1080

	private final Context context;
	private final CameraConfigurationManager configManager;
	private Camera camera;
	private AutoFocusManager autoFocusManager;
	private Rect framingRect;
	private Rect framingRectInPreview;
	private boolean initialized;
	private boolean previewing;
	private int requestedCameraId = -1;
	private int requestedFramingRectWidth;
	private int requestedFramingRectHeight;
	/**
	 * Preview frames are delivered here, which we pass on to the registered
	 * handler. Make sure to clear the handler so it will only receive one
	 * message.
	 */
	private final PreviewCallback previewCallback;

	public CameraManager(Context context) {
		this.context = context;
		this.configManager = new CameraConfigurationManager(context);
		previewCallback = new PreviewCallback(configManager);
	}

	/**
	 * Opens the camera driver and initializes the hardware parameters.
	 * 
	 * @param holder
	 *            The surface object which the camera will draw preview frames
	 *            into.
	 * @throws IOException
	 *             Indicates the camera driver failed to open.
	 */
	public synchronized void openDriver(SurfaceHolder holder,int cameraInfo)
			throws IOException {
		Camera theCamera = camera;
		if (theCamera == null) {

			if (cameraInfo >= 0) {
				theCamera = OpenCameraInterface.open(cameraInfo);
			} else {
				theCamera = OpenCameraInterface.open();
			}

			if (theCamera == null) {
				throw new IOException();
			}
			camera = theCamera;
			Log.d(TAG1, "Camera is "+ camera);
		}
		theCamera.setPreviewDisplay(holder);

		if (!initialized) {
//			initialized = true;
			configManager.initFromCameraParameters(theCamera);
			if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
				setManualFramingRect(requestedFramingRectWidth,
						requestedFramingRectHeight);
				requestedFramingRectWidth = 0;
				requestedFramingRectHeight = 0;
			}
		}

		Camera.Parameters parameters = theCamera.getParameters();
		String parametersFlattened = parameters == null ? null : parameters
				.flatten(); // Save these, temporarily
		try {
			configManager.setDesiredCameraParameters(theCamera, false);
		} catch (RuntimeException re) {
			// Driver failed
			Log.w(TAG,
					"Camera rejected parameters. Setting only minimal safe-mode parameters");
			Log.i(TAG, "Resetting to saved camera params: "
					+ parametersFlattened);
			// Reset:
			if (parametersFlattened != null) {
				parameters = theCamera.getParameters();
				parameters.unflatten(parametersFlattened);
				try {
					theCamera.setParameters(parameters);
					configManager.setDesiredCameraParameters(theCamera, true);
				} catch (RuntimeException re2) {
					// Well, darn. Give up
					Log.w(TAG,
							"Camera rejected even safe-mode parameters! No configuration");
				}
			}
		}
		Log.d(TAG1, "Camera is "+ camera);

	}

	public synchronized boolean isOpen() {
		return camera != null;
	}

	/**
	 * Closes the camera driver if still in use.
	 */
	public synchronized void closeDriver() {
		if (camera != null) {
			stopPreview();
			camera.release();
			camera = null;
			// Make sure to clear these each time we close the camera, so that
			// any scanning rect
			// requested by intent is forgotten.
			framingRect = null;
			framingRectInPreview = null;
		}
	}

	/**
	 * Asks the camera hardware to begin drawing preview frames to the screen.
	 */
	public synchronized void startPreview() {
		Camera theCamera = camera;
		if (theCamera != null && !previewing) {
			theCamera.startPreview();
			previewing = true;
			autoFocusManager = new AutoFocusManager(context, camera);
		}
	}

	/**
	 * Tells the camera to stop drawing preview frames.
	 */
	public synchronized void stopPreview() {
		if (autoFocusManager != null) {
			autoFocusManager.stop();
			autoFocusManager = null;
		}
		if (camera != null && previewing) {
			camera.stopPreview();
			previewCallback.setHandler(null, 0);
			previewing = false;
		}
	}

	/**
	 * A single preview frame will be returned to the handler supplied. The data
	 * will arrive as byte[] in the message.obj field, with width and height
	 * encoded as message.arg1 and message.arg2, respectively.
	 * 
	 * @param handler
	 *            The handler to send the message to.
	 * @param message
	 *            The what field of the message to be sent.
	 */
	public synchronized void requestPreviewFrame(Handler handler, int message) {
		Camera theCamera = camera;
		if (theCamera != null && previewing) {
			previewCallback.setHandler(handler, message);
			theCamera.setOneShotPreviewCallback(previewCallback);
		}
	}

	/**
	 * Calculates the framing rect which the UI should draw to show the user
	 * where to place the barcode. This target helps with alignment as well as
	 * forces the user to hold the device far enough away to ensure the image
	 * will be in focus. 计算这个条形码的扫描框；便于声明的同时，也强制用户通过改变距离来扫描到整个条形码
	 * 
	 * @return The rectangle to draw on screen in window coordinates.
	 */
	public synchronized Rect getFramingRect() {
		if (framingRect == null) {
			if (camera == null) {
				return null;
			}
			Point screenResolution = configManager.getScreenResolution();
			if (screenResolution == null) {
				// Called early, before init even finished
				return null;
			}

//			int width = findDesiredDimensionInRange(screenResolution.x,
//					MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
//			int height = findDesiredDimensionInRange(screenResolution.y,
//					MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);

			int width = screenResolution.x;
			int height = screenResolution.y;

			int leftOffset = (screenResolution.x - width) / 2;
			int topOffset = (screenResolution.y - height) / 2;
			framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
					topOffset + height);
			Log.d(TAG, "Calculated framing rect: " + framingRect);
		}
		return framingRect;
	}

	private static int findDesiredDimensionInRange(int resolution, int hardMin,
			int hardMax) {
		int dim = 5 * resolution / 8; // Target 8、/8 of each dimension
		if (dim < hardMin) {
			return hardMin;
		}
		if (dim > hardMax) {
			return hardMax;
		}
		return dim;
	}

	/**
	 * Like {@link #getFramingRect} but coordinates are in terms of the preview
	 * frame, not UI / screen.
	 * 
	 * @return {@link Rect} expressing barcode scan area in terms of the preview
	 *         size
	 */
	public synchronized Rect getFramingRectInPreview() {
		if (framingRectInPreview == null) {
			Rect framingRect = getFramingRect();
			if (framingRect == null) {
				return null;
			}
			Rect rect = new Rect(framingRect);
			Point cameraResolution = configManager.getCameraResolution();
			Point screenResolution = configManager.getScreenResolution();
			if (cameraResolution == null || screenResolution == null) {
				// Called early, before init even finished
				return null;
			}

			/******************** 竖屏更改1(cameraResolution.x/y互换) ************************/
			rect.left = rect.left * cameraResolution.y / screenResolution.x;
			rect.right = rect.right * cameraResolution.y / screenResolution.x;
			rect.top = rect.top * cameraResolution.x / screenResolution.y;
			rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
			framingRectInPreview = rect;
		}
		return framingRectInPreview;
	}

	/**
	 * Allows third party apps to specify the camera ID, rather than determine
	 * it automatically based on available cameras and their orientation.
	 * 
	 * @param cameraId
	 *            camera ID of the camera to use. A negative value means
	 *            "no preference".
	 */
	public synchronized void setManualCameraId(int cameraId) {
		requestedCameraId = cameraId;
	}

	/**
	 * Allows third party apps to specify the scanning rectangle dimensions,
	 * rather than determine them automatically based on screen resolution.
	 * 
	 * @param width
	 *            The width in pixels to scan.
	 * @param height
	 *            The height in pixels to scan.
	 */
	public synchronized void setManualFramingRect(int width, int height) {
		if (initialized) {
			Point screenResolution = configManager.getScreenResolution();
			if (width > screenResolution.x) {
				width = screenResolution.x;
			}
			if (height > screenResolution.y) {
				height = screenResolution.y;
			}
			int leftOffset = (screenResolution.x - width) / 2;
			int topOffset = (screenResolution.y - height) / 2;
			framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
					topOffset + height);
			Log.d(TAG, "Calculated manual framing rect: " + framingRect);
			framingRectInPreview = null;
		} else {
			requestedFramingRectWidth = width;
			requestedFramingRectHeight = height;
		}
	}

	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 * 
	 * @param data
	 *            A preview frame.
	 * @param width
	 *            The width of the image.
	 * @param height
	 *            The height of the image.
	 * @return A PlanarYUVLuminanceSource instance.
	 */
	public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data,
                                                         int width, int height) {
		Rect rect = getFramingRectInPreview();
		if (rect == null) {
			return null;
		}

        if (rect.left + rect.width() <= width && rect.top + rect.height() <= height){
            // Go ahead and assume it's YUV rather than die.
            Log.d(TAG,"width = "+width+" height = "+height+" rect.left = "+ rect.left +" rect.right = " + rect.right);
            return new PlanarYUVLuminanceSource(data, width, height, rect.left,
                    rect.top, rect.width(), rect.height(), false);
        }
        return null;
	}

	public  void setCameraDisplayOrientation(Activity activity) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(0, info);
		int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0:degrees = 0; break;
			case Surface.ROTATION_90: degrees = 90; break;
			case Surface.ROTATION_180: degrees = 180; break;
			case Surface.ROTATION_270: degrees = 270; break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360;  // compensate the mirror
		} else {  // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		camera.setDisplayOrientation(result);
	}



}
