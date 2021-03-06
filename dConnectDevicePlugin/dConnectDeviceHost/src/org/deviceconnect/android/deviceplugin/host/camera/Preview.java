/*
 Preview.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.host.camera;

import java.io.IOException;
import java.util.List;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.R;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * カメラのプレビューを表示するクラス.
 * 
 * @author NTT DOCOMO, INC.
 */
class Preview extends ViewGroup implements SurfaceHolder.Callback {
    /** デバック用タグ. */
    public static final String LOG_TAG = "DeviceConnectCamera:Preview";

    /**
     * プレビューの横幅の閾値を定義する.
     * <p>
     * これ以上の横幅のプレビューは設定させない。
     */
    private static final int THRESHOLD_WIDTH = 500;

    /**
     * プレビューの縦幅の閾値を定義する.
     * <p>
     * これ以上の縦幅のプレビューは設定させない。
     */
    private static final int THRESHOLD_HEIGHT = 400;

    /** プレビューを表示するSurfaceView. */
    private SurfaceView mSurfaceView;
    /** SurfaceViewを一時的に保持するホルダー. */
    private SurfaceHolder mHolder;
    /** プレビューのサイズ. */
    private Size mPreviewSize;
    /** サポートしているプレビューのサイズ. */
    private List<Size> mSupportedPreviewSizes;
    /** カメラのインスタンス. */
    private Camera mCamera;

    /**
     * ホストデバイスプラグインから渡されたリクエストID.<br>
     * - Broadcastで指示された場合は設定する。<br>
     * - アプリ内ならの指示ならnullを設定する。<br>
     */
    private String mRequestid;

    /**
     * コンストラクタ.
     * 
     * @param context このクラスが属するコンテキスト
     */
    public Preview(final Context context) {
        super(context);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
    }

    /**
     * Preview.
     * @param context context
     * @param attrs attributes
     */
    public Preview(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
    }

    /**
     * カメラのインスタンスを設定する.
     * 
     * @param camera カメラのインスタンス
     */
    public void setCamera(final Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            Point size = getDisplaySize(getContext());
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, size.x, size.y);
            requestLayout();
        }
    }

    /**
     * カメラのインスタンスを切り替えます.
     * 
     * @param camera 切り替えるカメラのインスタンス
     */
    public void switchCamera(final Camera camera) {
        setCamera(camera);
        try {
            camera.setPreviewDisplay(mHolder);
        } catch (IOException exception) {
            if (BuildConfig.DEBUG) {
                Log.e(LOG_TAG, "IOException caused by setPreviewDisplay()", exception);
            }
        }
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        requestLayout();

        camera.setParameters(parameters);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
        if (mSupportedPreviewSizes != null) {
            Point size = getDisplaySize(getContext());
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, size.x, size.y);
        }
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
            }

            // Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
            }
        }
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(LOG_TAG, "IOException caused by setPreviewDisplay()", e);
            }
        }
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    @Override
    public void surfaceChanged(final SurfaceHolder holder, final int format, final int w, final int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        if (mCamera != null) {
            int rot = getCameraDisplayOrientation(getContext());

            Camera.Parameters parameters = mCamera.getParameters();
            Size mPrevSize = parameters.getPreviewSize();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                Log.e(LOG_TAG, "This preview size not support. (" + mPreviewSize.width + ", "
                        + mPreviewSize.height + ")");
                parameters.setPreviewSize(mPrevSize.width, mPrevSize.height);
            }

            String mFocusMode = parameters.getFocusMode();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                Log.i(LOG_TAG, "Auto focus not support.");
                parameters.setFocusMode(mFocusMode);
                mCamera.setParameters(parameters);
            }

            parameters.setRotation(rot);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                Log.i(LOG_TAG, "Rotation not support.");
            }

            mCamera.setDisplayOrientation(rot);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                Log.i(LOG_TAG, "Display orientation not support.");
            }

            mCamera.startPreview();

            mPreviewFormat = parameters.getPreviewFormat();
        }
    }

    /**
     * プレビューのフォーマット.
     */
    private int mPreviewFormat;
    
    /**
     * プレビューのフォーマットを取得する.
     * @return プレビューのフォーマット
     */
    public int getPreviewFormat() {
        return mPreviewFormat;
    }

    /**
     * プレビューの横幅を取得する.
     * @return 横幅
     */
    public int getPreviewWidth() {
        return mPreviewSize.width;
    }

    /**
     * プレビューの縦幅を取得する.
     * @return 縦幅
     */
    public int getPreviewHeight() {
        return mPreviewSize.height;
    }

    /**
     * 最適なプレビューサイズを取得する. 指定されたサイズに最適なものがない場合にはnullを返却する。
     * 
     * @param sizes プレビューサイズ一覧
     * @param w 横幅
     * @param h 縦幅
     * @return 最適なプレビューサイズ
     */
    private Size getOptimalPreviewSize(final List<Size> sizes, final int w, final int h) {
        final double aspectTolerance = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) {
            return null;
        }

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > aspectTolerance) {
                continue;
            }
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    /**
     * 画面サイズを取得する.
     * @param context コンテキスト
     * @return 画面サイズ
     */
    private Point getDisplaySize(final Context context) {
        WindowManager mgr = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        mgr.getDefaultDisplay().getSize(size);
        if (size.x > THRESHOLD_WIDTH) {
            size.x = THRESHOLD_WIDTH;
        }
        if (size.y > THRESHOLD_HEIGHT) {
            size.y = THRESHOLD_HEIGHT;
        }
        return size;
    }

    /**
     * カメラの向きを取得する.
     * @param context コンテキスト
     * @return カメラの向き
     */
    public int getCameraDisplayOrientation(final Context context) {
        WindowManager mgr = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        int rot = mgr.getDefaultDisplay().getRotation();
        int base = 90;

        Configuration config = getContext().getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE 
                && (rot == Surface.ROTATION_0 || rot == Surface.ROTATION_180)) {
            base = 0;
        }

        int degree = 0;
        switch (rot) {
        default:
        case Surface.ROTATION_0:
            degree = 0;
            break;
        case Surface.ROTATION_90:
            degree = 90;
            break;
        case Surface.ROTATION_180:
            degree = 180;
            break;
        case Surface.ROTATION_270:
            degree = 270;
            break;
        }
        return (base + 360 - degree) % 360;
    }

    /**
     * 写真撮影を開始する.
     * 
     * @param callback callback.
     */
    public void takePicture(final Camera.PictureCallback callback) {
        if (mCamera != null) {
            mCamera.takePicture(mShutterCallback, null, callback);
            Toast.makeText(getContext(), R.string.shutter, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ズームイン処理を行う.
     * 
     * @param requestid リクエストID(Broadcastで指示された場合は設定する。アプリ内ならの指示ならnullを設定する)
     */
    public void zoomIn(final String requestid) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "zoomIn() start - requestid:" + requestid);
        }

        mRequestid = requestid;

        /* ズームイン処理 */
        Camera.Parameters parameters = mCamera.getParameters();
        int nowZoom = parameters.getZoom();
        if (nowZoom < parameters.getMaxZoom()) {
            parameters.setZoom(nowZoom + 1);
        }
        mCamera.setParameters(parameters);

        /* Toast表示 */
        String debugToast = getResources().getString(R.string.zoomin) + " requestid:" + mRequestid;
        Toast.makeText(getContext(), debugToast, Toast.LENGTH_SHORT).show();

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "zoomIn() end");
        }
    }

    /**
     * ズームアウト処理を行う.
     * 
     * @param requestid リクエストID(Broadcastで指示された場合は設定する。アプリ内ならの指示ならnullを設定する)
     */
    public void zoomOut(final String requestid) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "zoomOut() start - requestid:" + requestid);
        }

        mRequestid = requestid;

        /* ズームアウト処理 */
        Camera.Parameters parameters = mCamera.getParameters();
        int nowZoom = parameters.getZoom();
        if (nowZoom > 0) {
            parameters.setZoom(nowZoom - 1);
        }
        mCamera.setParameters(parameters);

        /* Toast表示 */
        String debugToast = getResources().getString(R.string.zoomout) + " requestid:" + mRequestid;
        Toast.makeText(getContext(), debugToast, Toast.LENGTH_SHORT).show();

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "zoomOut() end");
        }
    }

    /**
     * シャッターコールバック.
     * <p>
     * - シャッター音を鳴らすために使用する。
     */
    private final Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            // NOP
        }
    };
}
