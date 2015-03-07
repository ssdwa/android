/*
 HvcDetectThread.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hvc.comm;

import org.deviceconnect.android.deviceplugin.hvc.BuildConfig;
import org.deviceconnect.android.deviceplugin.hvc.humandetect.HumanDetectRequestParams;
import org.deviceconnect.android.deviceplugin.hvc.request.HvcDetectRequestParams;

import omron.HVC.HVC;
import omron.HVC.HVCBleCallback;
import omron.HVC.HVC_BLE;
import omron.HVC.HVC_PRM;
import omron.HVC.HVC_RES;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

/**
 * HVC Detect thread.
 * 
 * @author NTT DOCOMO, INC.
 */
public class HvcDetectThread extends Thread {
    
    /**
     * log tag.
     */
    private static final String TAG = HvcDetectThread.class.getSimpleName();

    /**
     * Constructor.
     */
    private Context mContext;
    /**
     * Bluetooth Device.
     */
    private BluetoothDevice mDevice;
    
    /**
     * Request parameters.
     */
    private HumanDetectRequestParams mRequestParams = null;
    
    
    /**
     * HVC detect listener.
     */
    private HvcDetectListener mListener;
    
    
    
    /**
     * HVC BLE class.
     */
    HVC_BLE mHvcBle = new HVC_BLE();
    /**
     * HVC parameter class.
     */
    HVC_PRM mHvcPrm = null;
    /**
     * HVC response class.
     */
    HVC_RES mHvcRes = new HVC_RES();
    
    /**
     * Cache parameter.
     */
    HVC_PRM mCacheHvcPrm = null;
    
    /**
     * Processing flag.
     */
    boolean mIsProcessing = false;
    
    
    
    
    /**
     * Wait start thread lock object.
     */
    private Object mLockWaitStartThread = new Object();
    
    /**
     * Wait request lock object.
     */
    private Object mLockWaitRequest = new Object();
    
    /**
     * Connect lock object.
     */
    private Object mLockWaitConnect = new Object();
    
    /**
     * Set parameter lock object.
     */
    private Object mLockWaitSetParameter = new Object();
    
    /**
     * Detect lock object.
     */
    private Object mLockWaitDetect = new Object();
    
    
    /**
     * Constructor.
     * @param context Context
     * @param device bluetooth device
     */
    public HvcDetectThread(final Context context, final BluetoothDevice device) {
        super();
        mContext = context;
        mDevice = device;
    }

    /**
     * thread start process.
     * @param requestParams request parameters
     * @param listener callback listener
     */
    public void request(final HumanDetectRequestParams requestParams, final HvcDetectListener listener) {
        mRequestParams = requestParams;
        mHvcPrm = new HvcDetectRequestParams(requestParams).getHvcParams();
        mListener = listener;
        
Log.d("AAA", "HvcDetectThread() - request() <1>");
        // if not alive, thread start.
        if (!isAlive()) {
            Log.d("AAA", "HvcDetectThread() - request() <2>");
            start();
            Log.d("AAA", "HvcDetectThread() - request() <3>");
            
            // wait start thread
            waitForStartThread();
            Log.d("AAA", "HvcDetectThread() - request() <4>");
            
            // first request, no need unlock.
            
        } else {
            Log.d("AAA", "HvcDetectThread() - request() <5>");
            
            // not connect process, unlock wait request.
            synchronized (mLockWaitRequest) {
                mLockWaitRequest.notifyAll();
            }
            
        }
        Log.d("AAA", "HvcDetectThread() - request() <6>");
        
    }

    /**
     * thread halt process.
     */
    public void halt() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "HvcDetectThread - halt()");
        }
        mHvcBle.disconnect();
        interrupt();
    }
    
    @Override
    public void run() {
Log.d("AAA", "HvcDetectThread - run() <1>");
        
        // unlock wait.
        synchronized (mLockWaitStartThread) {
            mLockWaitStartThread.notifyAll();
        }
Log.d("AAA", "HvcDetectThread - run() <2>");
        
        // BLE initialize (GATT)
        mHvcBle.setCallBack(new HVCBleCallback() {
            @Override
            public void onConnected() {
                super.onConnected();
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "HvcDetectThread - onConnected()");
                }
                mListener.onConnected();
                
                // unlock wait.
                synchronized (mLockWaitConnect) {
                    mLockWaitConnect.notifyAll();
                }
            }
            
            @Override
            public void onDisconnected() {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "HvcDetectThread - onDisconnected()");
                }
                mListener.onDisconnected();
                super.onDisconnected();
            }
            
            @Override
            public void onPostSetParam(final int nRet, final byte outStatus) {
                super.onPostSetParam(nRet, outStatus);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "HvcDetectThread - onPostSetParam()");
                }
                mListener.onPostSetParam(mHvcPrm);
                
                // Replace cache.
                mCacheHvcPrm = mHvcPrm;
                
                // unlock wait.
                synchronized (mLockWaitSetParameter) {
                    mLockWaitSetParameter.notifyAll();
                }
            }
            
            @Override
            public void onPostExecute(final int nRet, final byte outStatus) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "HvcDetectThread - onPostExecute() nRet:" + nRet + " outStatus:" + outStatus);
                }
                if (nRet != HVC.HVC_NORMAL || outStatus != 0) {
                    // Error processing
                    mListener.onDetectError(nRet);
                } else {
                    mListener.onDetectFinished(mHvcPrm, mHvcRes);
                }
                
                // unlock wait.
                synchronized (mLockWaitDetect) {
                    mLockWaitDetect.notifyAll();
                }
            }
        });
        
        // loop
        while (true) {
Log.d("AAA", "HvcDetectThread - run() <3>");
            
            // request process.
            mIsProcessing = true;
            requestProcessOnThread();
            mIsProcessing = false;
            
Log.d("AAA", "HvcDetectThread - run() <4>");

            // success (wait next request)
            waitForRequest();
            Log.d("AAA", "HvcDetectThread - run() <5>");
        }
        
    }

    /**
     * request process on thread.
     */
    private void requestProcessOnThread() {
        // connect
        int commStatus = mHvcBle.getStatus();
        if (commStatus == HVC.HVC_ERROR_NODEVICES) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "mHvcBle.connect()");
            }
            
            // clear cache.
            mCacheHvcPrm = null;
            
            // connect
            mHvcBle.connect(mContext, mDevice);
            
            // wait.
            waitForConnect();
        }
        
        // send parameter.
        if (mCacheHvcPrm == null || !mCacheHvcPrm.equals(mHvcPrm)) {
            
            // no hit cache, send parameter.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "mHvcBle.setParam()");
            }
            int result = mHvcBle.setParam(mHvcPrm);
            if (result != HVC.HVC_NORMAL) {
                mListener.onSetParamError(result);
                return;
            } else {
                // wait.
                waitForSendParameter();
            }
            
        }
        
        // send detect request.
        int useFunc = (new HvcDetectRequestParams(mRequestParams)).getUseFunc();
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "mHvcBle.execute() useFunc:" + useFunc);
        }
        int result = mHvcBle.execute(useFunc, mHvcRes);
        if (result != HVC.HVC_NORMAL) {
            mListener.onRequestDetectError(result);
            return;
        } else {
            // wait.
            waitForDetect();
        }
    }

    /**
     * get comm status.
     * @return comm status.
     */
    public int getHvcCommStatus() {
        int commStatus = mHvcBle.getStatus();
        return commStatus;
    }
    
    
    /**
     * wait start thread.
     */
    private void waitForStartThread() {
        synchronized (mLockWaitStartThread) {
            try {
                mLockWaitStartThread.wait();
            } catch (InterruptedException e) {
                return;
            }
        }
    }
    
    /**
     * wait for request.
     */
    private void waitForRequest() {
        synchronized (mLockWaitRequest) {
            try {
                mLockWaitRequest.wait();
            } catch (InterruptedException e) {
                return;
            }
        }
    }
    
    /**
     * wait for connect.
     */
    private void waitForConnect() {
        synchronized (mLockWaitConnect) {
            try {
                mLockWaitConnect.wait();
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * wait for send parameter.
     */
    private void waitForSendParameter() {
        synchronized (mLockWaitSetParameter) {
            try {
                mLockWaitSetParameter.wait();
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * wait for detect.
     */
    private void waitForDetect() {
        synchronized (mLockWaitDetect) {
            try {
                mLockWaitDetect.wait();
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * check busy.
     * @return true: busy / false: not busy
     */
    public boolean checkBusy() {
        return mIsProcessing;
    }
}
