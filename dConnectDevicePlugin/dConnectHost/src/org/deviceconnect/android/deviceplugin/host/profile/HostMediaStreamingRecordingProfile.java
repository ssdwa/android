/*
 HostMediaStreamingRecordingProfile.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.host.profile;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deviceconnect.android.deviceplugin.host.BuildConfig;
import org.deviceconnect.android.deviceplugin.host.HostDeviceService;
import org.deviceconnect.android.deviceplugin.host.audio.AudioConst;
import org.deviceconnect.android.deviceplugin.host.audio.AudioRecorder;
import org.deviceconnect.android.deviceplugin.host.camera.CameraActivity;
import org.deviceconnect.android.deviceplugin.host.camera.CameraConst;
import org.deviceconnect.android.deviceplugin.host.video.VideoConst;
import org.deviceconnect.android.deviceplugin.host.video.VideoRecorder;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventError;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.MediaStreamRecordingProfile;
import org.deviceconnect.message.DConnectMessage;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;

/**
 * MediaStream Recording Profile.
 * 
 * @author NTT DOCOMO, INC.
 */
public class HostMediaStreamingRecordingProfile extends MediaStreamRecordingProfile {
    /** ログを出力するかどうか. */
    public static final boolean LOG_DEBUG = true;

    /**
     * リクエストマップ. - Key: カメラリクエストID - Val: カメラ応答Broadcast 未受信ならnull /
     * 受信済なら画像URI(画像ID)
     */
    private static Map<String, String> mRequestMap = new ConcurrentHashMap<String, String>();

    /**
     * リクエストマップを取得する.
     * 
     * @return リクエストマップ
     */
    public static Map<String, String> getRequestMap() {
        return mRequestMap;
    }

    @Override
    protected boolean onGetMediaRecorder(final Intent request, final Intent response, final String deviceId) {
        if (deviceId == null) {
            createEmptyDeviceId(response);
        } else if (!checkdeviceId(deviceId)) {
            createNotFoundDevice(response);
        } else {

            List<Bundle> recorders = new LinkedList<Bundle>();

            Bundle recorder = new Bundle();

            setRecorderId(recorder, "001");
            setRecorderName(recorder, "AndroidHost Recorder");
            setRecorderImageWidth(recorder, VideoConst.VIDEO_WIDTH);
            setRecorderImageHeight(recorder, VideoConst.VIDEO_HEIGHT);
            setRecorderConfig(recorder, "");
            recorders.add(recorder);

            setRecorders(response, recorders.toArray(new Bundle[recorders.size()]));

            setResult(response, DConnectMessage.RESULT_OK);
        }
        return true;
    }

    @Override
    protected boolean onPutOnPhoto(final Intent request, final Intent response, final String deviceId,
            final String sessionKey) {

        // イベントの登録
        EventError error = EventManager.INSTANCE.addEvent(request);

        if (error == EventError.NONE) {
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        } else {
            setResult(response, DConnectMessage.RESULT_ERROR);
            return true;
        }
    }

    @Override
    protected boolean onDeleteOnPhoto(final Intent request, final Intent response, final String deviceId,
            final String sessionKey) {

        // イベントの解除
        EventError error = EventManager.INSTANCE.removeEvent(request);
        if (error == EventError.NONE) {
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        } else {
            setResult(response, DConnectMessage.RESULT_ERROR);
            return true;
        }
    }

    @Override
    protected boolean onPostTakePhoto(final Intent request, final Intent response, final String deviceId,
            final String target) {
        // カメラアプリにシャッター通知
        final String requestid = "" + UUID.randomUUID().hashCode();

        if (deviceId == null) {
            createEmptyDeviceId(response);
            return true;
        } else if (!checkDeviceId(deviceId)) {
            createNotFoundDevice(response);
            return true;
        } else {

            String mClassName = getClassnameOfTopActivity();

            // カメラアプリがすでに前にある
            if (CameraActivity.class.getName().equals(mClassName)) {
                Intent mIntent = new Intent();
                mIntent.setClass(getContext(), CameraActivity.class);
                mIntent.setAction(CameraConst.SEND_HOSTDP_TO_CAMERA);
                mIntent.putExtra(CameraConst.EXTRA_NAME, CameraConst.EXTRA_NAME_SHUTTER);
                mIntent.putExtra(CameraConst.EXTRA_REQUESTID, requestid);
                getContext().sendBroadcast(mIntent);
            } else {
                Intent mIntent = new Intent();
                mIntent.setClass(getContext(), CameraActivity.class);
                mIntent.setAction(CameraConst.SEND_HOSTDP_TO_CAMERA);
                mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mIntent.putExtra(CameraConst.EXTRA_NAME, CameraConst.EXTRA_NAME_SHUTTER);
                mIntent.putExtra(CameraConst.EXTRA_REQUESTID, requestid);
                getContext().startActivity(mIntent);
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    final long POLLING_WAIT_TIMEOUT = 10000;
                    final long POLLING_WAIT_TIME = 500;
                    long now = System.currentTimeMillis();
                    try {
                        do {
                            Thread.sleep(POLLING_WAIT_TIME);
                        } while (mRequestMap.get(requestid) == null
                                && System.currentTimeMillis() - now < POLLING_WAIT_TIMEOUT);
                    } catch (InterruptedException e) {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                    }

                    String pictureUri = mRequestMap.remove(requestid);
                    if (pictureUri == null) {
                        setResult(response, DConnectMessage.RESULT_ERROR);
                        getContext().sendBroadcast(response);
                        return;
                    }

                    // レスポンスを返す
                    setResult(response, DConnectMessage.RESULT_OK);
                    setUri(response, pictureUri);
                    getContext().sendBroadcast(response);

                    List<Event> events = EventManager.INSTANCE.getEventList(deviceId,
                            PROFILE_NAME, null, ATTRIBUTE_ON_PHOTO);
                    for (int i = 0; i < events.size(); i++) {
                        Intent mIntent = EventManager.createEventMessage(events.get(i));
                        Bundle photo = new Bundle();
                        setPath(photo, pictureUri);
                        setMIMEType(photo, "image/png");
                        setPhoto(mIntent, photo);
                        getContext().sendBroadcast(mIntent);
                    }
                }
            }).start();
        }

        mLogger.exiting(getClass().getName(), "onPostReceive", false);
        return false;

    }

    @Override
    protected boolean onPutPreview(Intent request, Intent response, String deviceId) {
        if (deviceId == null) {
            createEmptyDeviceId(response);
            return true;
        } else if (!checkDeviceId(deviceId)) {
            createNotFoundDevice(response);
            return true;
        } else {
            String uri = ((HostDeviceService) getContext()).startWebServer();
            if (uri != null) {
                setResult(response, DConnectMessage.RESULT_OK);
                setUri(response, uri);
            } else {
                MessageUtils.setIllegalServerStateError(response, 
                        "Failed to start web server.");
            }

            String className = getClassnameOfTopActivity();
            if (CameraActivity.class.getName().equals(className)) {
                Intent mIntent = new Intent();
                mIntent.setAction(CameraConst.SEND_HOSTDP_TO_CAMERA);
                mIntent.putExtra(CameraConst.EXTRA_NAME, CameraConst.EXTRA_NAME_PREVIEW);
                getContext().sendBroadcast(mIntent);
            } else {
                Intent mIntent = new Intent();
                mIntent.setClass(getContext(), CameraActivity.class);
                mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mIntent.setAction(CameraConst.SEND_HOSTDP_TO_CAMERA);
                mIntent.putExtra(CameraConst.EXTRA_NAME, CameraConst.EXTRA_NAME_PREVIEW);
                getContext().startActivity(mIntent);
            }
            return true;
        }
    }

    @Override
    protected boolean onDeletePreview(Intent request, Intent response, String deviceId) {
        if (deviceId == null) {
            createEmptyDeviceId(response);
            return true;
        } else if (!checkDeviceId(deviceId)) {
            createNotFoundDevice(response);
            return true;
        } else {
            ((HostDeviceService) getContext()).stopWebServer();
            String className = getClassnameOfTopActivity();
            if (CameraActivity.class.getName().equals(className)) {
                Intent mIntent = new Intent();
                mIntent.setAction(CameraConst.SEND_HOSTDP_TO_CAMERA);
                mIntent.putExtra(CameraConst.EXTRA_NAME, CameraConst.EXTRA_NAME_FINISH);
                getContext().sendBroadcast(mIntent);
            }
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }
    }

    @Override
    protected boolean onPostRecord(final Intent request, final Intent response, final String deviceId,
            final String target, final Long timeslice) {

        if (deviceId == null) {
            createEmptyDeviceId(response);
            return true;
        } else if (!checkDeviceId(deviceId)) {
            createNotFoundDevice(response);
            return true;
        } else {
            String mClassName = getClassnameOfTopActivity();

            if (target == null || target.equals("video")) {
                if (VideoRecorder.class.getName().equals(mClassName)) {
                    MessageUtils.setError(response, 100, "Running video recoder, yet");
                    return true;
                }
                Intent mIntent = new Intent();
                mIntent.setClass(getContext(), VideoRecorder.class);
                mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(mIntent);
                setResult(response, DConnectMessage.RESULT_OK);
            } else if (target.equals("audio")) {
                if (AudioRecorder.class.getName().equals(mClassName)) {
                    MessageUtils.setError(response, 100, "Running audio recoder, yet");
                    return true;
                }

                Intent mIntent = new Intent();
                mIntent.setClass(getContext(), AudioRecorder.class);
                mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(mIntent);
                setResult(response, DConnectMessage.RESULT_OK);
            }

            return true;
        }
    }

    @Override
    protected boolean onPutStop(final Intent request, final Intent response, final String deviceId,
                                final String target) {
        if (deviceId == null) {
            createEmptyDeviceId(response);
            return true;
        } else if (!checkDeviceId(deviceId)) {
            createNotFoundDevice(response);
            return true;
        } else {

            // 今起動しているActivityを判定する
            String mClassName = getClassnameOfTopActivity();

            if (VideoRecorder.class.getName().equals(mClassName)) {
                Intent mIntent = new Intent(VideoConst.SEND_HOSTDP_TO_VIDEO);
                mIntent.putExtra(VideoConst.EXTRA_NAME, VideoConst.EXTRA_VALUE_VIDEO_RECORD_STOP);
                getContext().sendBroadcast(mIntent);
                setResult(response, DConnectMessage.RESULT_OK);
            } else if (AudioRecorder.class.getName().equals(mClassName)) {
                Intent mIntent = new Intent(AudioConst.SEND_HOSTDP_TO_AUDIO);
                mIntent.putExtra(AudioConst.EXTRA_NAME, AudioConst.EXTRA_NAME_AUDIO_RECORD_STOP);
                getContext().sendBroadcast(mIntent);
                setResult(response, DConnectMessage.RESULT_OK);
            }

            return true;
        }
    }

    @Override
    protected boolean onPutPause(final Intent request, final Intent response, final String deviceId,
                            final String target) {

        if (deviceId == null) {
            createEmptyDeviceId(response);
            return true;
        } else if (!checkDeviceId(deviceId)) {
            createNotFoundDevice(response);
            return true;
        } else {

            String mClassName = getClassnameOfTopActivity();

            if (VideoRecorder.class.getName().equals(mClassName)) {
                MessageUtils.setError(response, 201, "not support");
            } else if (AudioRecorder.class.getName().equals(mClassName)) {
                Intent mIntent = new Intent(AudioConst.SEND_HOSTDP_TO_AUDIO);
                mIntent.putExtra(AudioConst.EXTRA_NAME, AudioConst.EXTRA_NAME_AUDIO_RECORD_PAUSE);
                getContext().sendBroadcast(mIntent);
                setResult(response, DConnectMessage.RESULT_OK);
            }

            return true;
        }
    }

    @Override
    protected boolean onPutResume(final Intent request, final Intent response, final String deviceId,
            final String target) {

        if (deviceId == null) {
            createEmptyDeviceId(response);
            return true;
        } else if (!checkDeviceId(deviceId)) {
            createNotFoundDevice(response);
            return true;
        } else {

            String mClassName = getClassnameOfTopActivity();
            if (VideoRecorder.class.getName().equals(mClassName)) {
                MessageUtils.setError(response, 201, "not support");
            } else if (AudioRecorder.class.getName().equals(mClassName)) {
                Intent intent = new Intent(AudioConst.SEND_HOSTDP_TO_AUDIO);
                intent.putExtra(AudioConst.EXTRA_NAME, AudioConst.EXTRA_NAME_AUDIO_RECORD_RESUME);
                getContext().sendBroadcast(intent);
                setResult(response, DConnectMessage.RESULT_OK);
            }
        }

        return true;
    }

    /**
     * デバイスIDをチェックする.
     * 
     * @param deviceId デバイスID
     * @return <code>deviceId</code>がテスト用デバイスIDに等しい場合はtrue、そうでない場合はfalse
     */
    private boolean checkDeviceId(final String deviceId) {
        String regex = HostNetworkServiceDiscoveryProfile.DEVICE_ID;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(deviceId);

        return m.find();
    }

    /**
     * 画面の一番上にでているActivityのクラス名を取得.
     * 
     * @return クラス名
     */
    private String getClassnameOfTopActivity() {

        ActivityManager mActivityManager = (ActivityManager) getContext().getSystemService(Service.ACTIVITY_SERVICE);
        String mClassName = mActivityManager.getRunningTasks(1).get(0).topActivity.getClassName();

        return mClassName;
    }

    /**
     * デバイスが発見できなかった場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createNotFoundDevice(final Intent response) {
        MessageUtils.setNotFoundDeviceError(response, "Device is not found.");
    }

    /**
     * デバイスIDをチェックする.
     * 
     * @param deviceId デバイスID
     * @return <code>deviceId</code>がテスト用デバイスIDに等しい場合はtrue、そうでない場合はfalse
     */
    private boolean checkdeviceId(final String deviceId) {
        return HostNetworkServiceDiscoveryProfile.DEVICE_ID.equals(deviceId);
    }

    /**
     * デバイスIDが空の場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createEmptyDeviceId(final Intent response) {
        MessageUtils.setEmptyDeviceIdError(response);
    }
}
