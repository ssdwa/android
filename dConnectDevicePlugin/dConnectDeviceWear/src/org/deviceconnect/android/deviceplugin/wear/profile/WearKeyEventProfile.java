/*
 WearKeyEventProfile.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.wear.profile;

import java.util.List;

import org.deviceconnect.android.deviceplugin.wear.BuildConfig;
import org.deviceconnect.android.deviceplugin.wear.WearDeviceService;
import org.deviceconnect.android.deviceplugin.wear.WearManager;
import org.deviceconnect.android.deviceplugin.wear.WearManager.OnMessageEventListener;
import org.deviceconnect.android.deviceplugin.wear.WearManager.OnMessageResultListener;
import org.deviceconnect.android.event.Event;
import org.deviceconnect.android.event.EventError;
import org.deviceconnect.android.event.EventManager;
import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.KeyEventProfile;
import org.deviceconnect.message.DConnectMessage;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.wearable.MessageApi.SendMessageResult;

/**
 * Key Event Profile.
 * 
 * @author NTT DOCOMO, INC.
 */
public class WearKeyEventProfile extends KeyEventProfile {

    /** Tag. */
    private static final String TAG = "WEAR";

    /**
     * Receive event listener for Android Wear.
     */
    private OnMessageEventListener mListener = new OnMessageEventListener() {
        @Override
        public void onEvent(final String nodeId, final String message) {
            sendMessageToEvent(WearUtils.createServiceId(nodeId), message);
        }
    };

    /**
     * Constructor.
     * 
     * @param mgr Android Wear management class.
     */
    public WearKeyEventProfile(final WearManager mgr) {
        mgr.addMessageEventListener(WearConst.WEAR_TO_DEVICE_KEYEVENT_DATA, mListener);
    }

    @Override
    protected boolean onPutOnDown(final Intent request, final Intent response, final String serviceId,
            final String sessionKey) {
        if (serviceId == null) {
            MessageUtils.setEmptyServiceIdError(response);
            return true;
        } else if (!WearUtils.checkServiceId(serviceId)) {
            MessageUtils.setNotFoundServiceError(response);
            return true;
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response);
            return true;
        } else {
            String nodeId = WearUtils.getNodeId(serviceId);
            getManager().sendMessageToWear(nodeId, WearConst.DEVICE_TO_WEAR_KEYEVENT_ONDOWN_REGISTER, "",
                    new OnMessageResultListener() {
                        @Override
                        public void onResult(final SendMessageResult result) {
                            if (result.getStatus().isSuccess()) {
                                EventError error = EventManager.INSTANCE.addEvent(request);
                                if (error == EventError.NONE) {
                                    setResult(response, DConnectMessage.RESULT_OK);
                                } else {
                                    setResult(response, DConnectMessage.RESULT_ERROR);
                                }
                            } else {
                                MessageUtils.setIllegalDeviceStateError(response);
                            }
                            getContext().sendBroadcast(response);
                        }

                        @Override
                        public void onError() {
                            MessageUtils.setIllegalDeviceStateError(response);
                            getContext().sendBroadcast(response);
                        }
                    });
            return false;
        }
    }

    @Override
    protected boolean onPutOnUp(final Intent request, final Intent response, final String serviceId,
            final String sessionKey) {
        if (serviceId == null) {
            MessageUtils.setEmptyServiceIdError(response);
            return true;
        } else if (!WearUtils.checkServiceId(serviceId)) {
            MessageUtils.setNotFoundServiceError(response);
            return true;
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response);
            return true;
        } else {
            String nodeId = WearUtils.getNodeId(serviceId);
            getManager().sendMessageToWear(nodeId, WearConst.DEVICE_TO_WEAR_KEYEVENT_ONUP_REGISTER, "",
                    new OnMessageResultListener() {
                        @Override
                        public void onResult(final SendMessageResult result) {
                            if (result.getStatus().isSuccess()) {
                                EventError error = EventManager.INSTANCE.addEvent(request);
                                if (error == EventError.NONE) {
                                    setResult(response, DConnectMessage.RESULT_OK);
                                } else {
                                    setResult(response, DConnectMessage.RESULT_ERROR);
                                }
                            } else {
                                MessageUtils.setIllegalDeviceStateError(response);
                            }
                            getContext().sendBroadcast(response);
                        }

                        @Override
                        public void onError() {
                            MessageUtils.setIllegalDeviceStateError(response);
                            getContext().sendBroadcast(response);
                        }
                    });
            return false;
        }
    }

    @Override
    protected boolean onDeleteOnDown(final Intent request, final Intent response, final String serviceId,
            final String sessionKey) {
        if (serviceId == null) {
            MessageUtils.setEmptyServiceIdError(response);
        } else if (!WearUtils.checkServiceId(serviceId)) {
            MessageUtils.setNotFoundServiceError(response);
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response);
        } else {
            String nodeId = WearUtils.getNodeId(serviceId);
            getManager().sendMessageToWear(nodeId, WearConst.DEVICE_TO_WEAR_KEYEVENT_ONDOWN_UNREGISTER, "",
                    new OnMessageResultListener() {
                        @Override
                        public void onResult(final SendMessageResult result) {
                        }

                        @Override
                        public void onError() {
                        }
                    });

            // Event release.
            EventError error = EventManager.INSTANCE.removeEvent(request);
            if (error == EventError.NONE) {
                setResult(response, DConnectMessage.RESULT_OK);
            } else {
                setResult(response, DConnectMessage.RESULT_ERROR);
            }
        }
        return true;
    }

    @Override
    protected boolean onDeleteOnUp(final Intent request, final Intent response, final String serviceId,
            final String sessionKey) {
        if (serviceId == null) {
            MessageUtils.setEmptyServiceIdError(response);
        } else if (!WearUtils.checkServiceId(serviceId)) {
            MessageUtils.setNotFoundServiceError(response);
        } else if (sessionKey == null) {
            MessageUtils.setInvalidRequestParameterError(response);
        } else {
            String nodeId = WearUtils.getNodeId(serviceId);
            getManager().sendMessageToWear(nodeId, WearConst.DEVICE_TO_WEAR_KEYEVENT_ONUP_UNREGISTER, "",
                    new OnMessageResultListener() {
                        @Override
                        public void onResult(final SendMessageResult result) {
                        }

                        @Override
                        public void onError() {
                        }
                    });

            // Event release.
            EventError error = EventManager.INSTANCE.removeEvent(request);
            if (error == EventError.NONE) {
                setResult(response, DConnectMessage.RESULT_OK);
            } else {
                setResult(response, DConnectMessage.RESULT_ERROR);
            }
        }
        return true;
    }

    /**
     * Send a message to the registration event.
     * 
     * @param nodeId node id
     * @param data Received Strings.
     */
    private void sendMessageToEvent(final String nodeId, final String data) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "@@@@@@SUCCESS");
        }

        String[] mDataArray = data.split(",", 0);
        String attr = null;
        if (mDataArray[0].equals(WearConst.PARAM_KEYEVENT_DOWN)) {
            attr = ATTRIBUTE_ON_DOWN;
        } else if (mDataArray[0].equals(WearConst.PARAM_KEYEVENT_UP)) {
            attr = ATTRIBUTE_ON_UP;
        } else {
            return;
        }

        List<Event> events = EventManager.INSTANCE.getEventList(nodeId, PROFILE_NAME, null, attr);
        synchronized (events) {
            for (Event event : events) {
                Bundle keyevent = new Bundle();

                keyevent.putInt(KeyEventProfile.PARAM_ID, Integer.parseInt(mDataArray[1]));
                keyevent.putString(KeyEventProfile.PARAM_CONFIG, mDataArray[2]);

                Intent intent = EventManager.createEventMessage(event);
                intent.putExtra(KeyEventProfile.PARAM_KEYEVENT, keyevent);
                ((WearDeviceService) getContext()).sendEvent(intent, event.getAccessToken());
            }
        }
    }

    /**
     * Get Android Wear management class.
     * 
     * @return WearManager management class.
     */
    private WearManager getManager() {
        return ((WearDeviceService) getContext()).getManager();
    }
}
