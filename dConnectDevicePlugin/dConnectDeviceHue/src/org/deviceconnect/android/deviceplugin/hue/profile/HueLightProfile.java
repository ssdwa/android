/*
HueLightProfile
Copyright (c) 2014 NTT DOCOMO,INC.
Released under the MIT license
http://opensource.org/licenses/mit-license.php
 */

package org.deviceconnect.android.deviceplugin.hue.profile;

import android.content.Intent;
import android.os.Bundle;

import com.philips.lighting.hue.listener.PHGroupListener;
import com.philips.lighting.hue.listener.PHLightListener;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.hue.sdk.utilities.PHUtilities;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeResource;
import com.philips.lighting.model.PHBridgeResourcesCache;
import com.philips.lighting.model.PHGroup;
import com.philips.lighting.model.PHHueError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLight.PHLightColorMode;
import com.philips.lighting.model.PHLightState;

import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.original.profile.LightProfile;
import org.deviceconnect.message.DConnectMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 親クラスで振り分けられたメソッドに対して、Hueのlight attribute処理を呼び出す.
 * 
 * @author NTT DOCOMO, INC.
 */
public class HueLightProfile extends LightProfile {

    /** エラーコード301. */
    private static final int HUE_SDK_ERROR_301 = 301;
    /** RGBの文字列の長さ. */
    private static final int RGB_LENGTH = 6;

    @Override
    protected boolean onGetLight(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }
        List<Bundle> lightsParam = new ArrayList<Bundle>();
        for (PHLight light : bridge.getResourceCache().getAllLights()) {
            Bundle lightParam = new Bundle();
            lightParam.putString(PARAM_LIGHT_ID, light.getIdentifier());
            lightParam.putString(PARAM_NAME, light.getName());
            lightParam.putString(PARAM_CONFIG, "");
            PHLightState state = light.getLastKnownLightState();
            lightParam.putBoolean(PARAM_ON, state.isOn());
            lightsParam.add(lightParam);
        }
        response.putExtra(PARAM_LIGHTS, lightsParam.toArray(new Bundle[lightsParam.size()]));
        sendResultOK(response);
        return false;
    }

    @Override
    protected boolean onPostLight(final Intent request, final Intent response) {
        int[] colorParam = new int[3];
        float brightness = 0;
        String serviceId = getServiceID(request);
        String lightId = getLightID(request);

        // 必須パラメータの存在チェック
        if (lightId == null || lightId.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "lightId is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }
        PHLight light = bridge.getResourceCache().getLights().get(lightId);
        if (light == null) {
            MessageUtils.setInvalidRequestParameterError(response, "Not found light: " + lightId + "@" + serviceId);
            return true;
        }

        brightness = getBrightnessParam(request, response);
        if (brightness == -1) {
            MessageUtils.setInvalidRequestParameterError(response, "brightness is not set.");
            return true;
        }

        if (!(getColorParam(request, response, colorParam))) {
            MessageUtils.setInvalidRequestParameterError(response, "color is invalid.");
            return true;
        }

        // Brightness magnification conversion
        String color = calcColorParam(colorParam, brightness);

        // Calculation of brightness.
        int mCalcBrightness = calcBrightnessParam(colorParam);

        PHLightState lightState = new PHLightState();
        lightState.setOn(true);
        lightState.setColorMode(PHLightColorMode.COLORMODE_XY);

        Color hueColor = new Color(color);
        lightState.setX(hueColor.mX);
        lightState.setY(hueColor.mY);
        lightState.setBrightness(mCalcBrightness);

        bridge.updateLightState(light, lightState, new PHLightAdapter() {
            @Override
            public void onStateUpdate(final Map<String, String> successAttribute,
                    final List<PHHueError> errorAttribute) {
                sendResultOK(response);
            }
            @Override
            public void onError(final int code, final String message) {
                MessageUtils.setUnknownError(response, code + ": " + message);
                sendResultERR(response);
            }
        });
        return false;
    }

    @Override
    protected boolean onDeleteLight(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        String lightId = getLightID(request);

        // 必須パラメータの存在チェック
        if (lightId == null || lightId.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "lightId is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }

        PHLight light = bridge.getResourceCache().getLights().get(lightId);
        if (light == null) {
            MessageUtils.setInvalidRequestParameterError(response, "Not found light: " + lightId + "@" + serviceId);
            return true;
        }

        PHLightState lightState = new PHLightState();
        lightState.setOn(false);
        bridge.updateLightState(light, lightState, new PHLightAdapter() {
            @Override
            public void onStateUpdate(final Map<String, String> successAttribute,
                    final List<PHHueError> errorAttribute) {
                sendResultOK(response);
            }
            @Override
            public void onError(final int code, final String message) {
                String errMsg = "ライトの状態更新に失敗しました hue:code = " + Integer.toString(code) + "  message = " + message;
                MessageUtils.setUnknownError(response, errMsg);
                sendResultERR(response);
            }
        });
        return false;
    }

    @Override
    protected boolean onPutLight(final Intent request, final Intent response) {
        int[] colorParam = new int[3];
        float brightness = 0;
        String serviceId = getServiceID(request);
        String lightId = getLightID(request);
        String name = getName(request);

        // 必須パラメータの存在チェック
        if (lightId == null || lightId.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "lightId is not specified.");
            return true;
        }
        if (name == null || name.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "name is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }

        brightness = getBrightnessParam(request, response);
        if (brightness == -1) {
            MessageUtils.setInvalidRequestParameterError(response, "brightness is not set.");
            return true;
        }

        if (!(getColorParam(request, response, colorParam))) {
            MessageUtils.setInvalidRequestParameterError(response, "color is invalid.");
            return true;
        }

        PHLight light = getLight(bridge, lightId);
        if (light == null) {
            MessageUtils.setInvalidRequestParameterError(response, "Not found light: " + lightId + "@" + serviceId);
            return true;
        }

        // Set response count.
        setResponseCount(2);
        setResponse(response);

        PHLight newLight = new PHLight(light);
        newLight.setName(name);
        bridge.updateLight(newLight, mAdaptor);

        // Brightness magnification conversion
        String color = calcColorParam(colorParam, brightness);

        // Calculation of brightness.
        int mCalcBrightness = calcBrightnessParam(colorParam);

        PHLightState lightState = new PHLightState();
        lightState.setOn(true);
        lightState.setColorMode(PHLightColorMode.COLORMODE_XY);
        Color hueColor = new Color(color);
        lightState.setX(hueColor.mX);
        lightState.setY(hueColor.mY);
        lightState.setBrightness(mCalcBrightness);

        bridge.updateLightState(light, lightState, mAdaptor);
        return false;
    }

    /** Response intent backup. */
    private static Intent sResponse = null;

    /**
     * Get Response intent.
     * 
     * @return response intent.
     */
    private static Intent getResponse() {
        return sResponse;
    }

    /**
     * Set response intent.
     * 
     * @param response Response intent.
     */
    private static void setResponse(final Intent response) {
        sResponse = response;
    }

    /** Response counter. */
    private static int sResponseCount = 0;

    /**
     * Get Response count.
     * 
     * @return Response count.
     */
    private static int getResponseCount() {
        return --sResponseCount;
    }

    /**
     * Set response count.
     * 
     * @param count Response count.
     */
    private static void setResponseCount(final int count) {
        sResponseCount = count;
    }

    /**
     *  Bridge response listener.
     */
    private PHLightAdapter mAdaptor = new PHLightAdapter() {
        @Override
        public void onSuccess() {
            if (getResponseCount() == 0) {
                sendResultOK(getResponse());
            }
        }

        @Override
        public void onStateUpdate(final Map<String, String> successAttribute, final List<PHHueError> errorAttribute) {
            if (getResponseCount() == 0) {
                sendResultOK(getResponse());
            }
        }

        @Override
        public void onError(final int code, final String message) {
            int count = getResponseCount();
            if (count == 1) {
                setResponseCount(0); // Reset response counter.
            } else if (count != 0) {
                return; // Already send response.
            }

            Intent response = getResponse();
            String errMsg = "ライトの状態更新に失敗しました hue:code = " + Integer.toString(code) + "  message = " + message;
            MessageUtils.setUnknownError(response, errMsg);
            sendResultERR(response);
        }
    };

    @Override
    protected boolean onGetLightGroup(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }
        List<Bundle> groupsParam = new ArrayList<Bundle>();
        Map<String, PHLight> allLights = bridge.getResourceCache().getLights();
        for (PHGroup group : bridge.getResourceCache().getAllGroups()) {
            Bundle groupParam = new Bundle();
            groupParam.putString(PARAM_GROUP_ID, group.getIdentifier());
            groupParam.putString(PARAM_NAME, group.getName());
            List<Bundle> lightsParam = new ArrayList<Bundle>();
            for (String lightId : group.getLightIdentifiers()) {
                PHLight light = allLights.get(lightId);
                if (light != null) {
                    Bundle lightParam = new Bundle();
                    lightParam.putString(PARAM_LIGHT_ID, lightId);
                    lightParam.putString(PARAM_NAME, light.getName());
                    PHLightState state = light.getLastKnownLightState();
                    if (state != null) {
                        lightParam.putBoolean(PARAM_ON, state.isOn().booleanValue());
                    }
                    lightParam.putString(PARAM_CONFIG, "");
                    lightsParam.add(lightParam);
                }
            }
            groupParam.putParcelableArray(PARAM_LIGHTS, lightsParam.toArray(new Bundle[lightsParam.size()]));
            groupParam.putString(PARAM_CONFIG, "");
            groupsParam.add(groupParam);
        }
        response.putExtra(PARAM_LIGHT_GROUPS, groupsParam.toArray(new Bundle[groupsParam.size()]));
        setResult(response, DConnectMessage.RESULT_OK);
        return true;
    }

    @Override
    protected boolean onPostLightGroup(final Intent request, final Intent response) {
        int[] colorParam = new int[3];
        float brightness = 0;
        String serviceId = getServiceID(request);
        String groupId = getGroupId(request);

        // 必須パラメータの存在チェック
        if (groupId == null || groupId.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "groupId is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }

        brightness = getBrightnessParam(request, response);
        if (brightness == -1) {
            return true;
        }

        if (!(getColorParam(request, response, colorParam))) {
            return true;
        }

        // Brightness magnification conversion
        String color = calcColorParam(colorParam, brightness);

        // Calculation of brightness.
        int mCalcBrightness = calcBrightnessParam(colorParam);

        PHLightState lightState = new PHLightState();
        lightState.setOn(true);
        lightState.setColorMode(PHLightColorMode.COLORMODE_XY);
        Color hueColor = new Color(color);
        lightState.setX(hueColor.mX);
        lightState.setY(hueColor.mY);
        lightState.setBrightness(mCalcBrightness);

        if ("0".equals(groupId)) {
            bridge.setLightStateForDefaultGroup(lightState);
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }

        PHGroup group = getGroup(bridge, groupId);
        if (group == null) {
            MessageUtils.setUnknownError(response, "Not found group: " + groupId);
            return true;
        }
        bridge.setLightStateForGroup(group.getIdentifier(), lightState, new PHGroupAdapter() {
            @Override
            public void onError(final int code, final String message) {
                String msg = "ライトの状態更新に失敗しました hue:code = " + Integer.toString(code) + "  message = " + message;
                MessageUtils.setUnknownError(response, msg);
                sendResultERR(response);
            }

            @Override
            public void onStateUpdate(final Map<String, String> successAttributes,
                    final List<PHHueError> errorAttributes) {
                sendResultOK(response);
            }
        });
        return false;
    }

    @Override
    protected boolean onDeleteLightGroup(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        String groupId = getGroupId(request);

        // 必須パラメータの存在チェック
        if (groupId == null || groupId.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "groupId is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }

        PHLightState lightState = new PHLightState();
        lightState.setOn(false);

        if ("0".equals(groupId)) {
            bridge.setLightStateForDefaultGroup(lightState);
            setResult(response, DConnectMessage.RESULT_OK);
            return true;
        }

        PHGroup group = getGroup(bridge, groupId);
        if (group == null) {
            MessageUtils.setUnknownError(response, "Not found group: " + groupId);
            return true;
        }
        bridge.setLightStateForGroup(group.getIdentifier(), lightState, new PHGroupAdapter() {
            @Override
            public void onError(final int code, final String message) {
                String msg = "ライトの状態更新に失敗しました hue:code = " + Integer.toString(code) + "  message = " + message;
                MessageUtils.setUnknownError(response, msg);
                sendResultERR(response);
            }

            @Override
            public void onStateUpdate(final Map<String, String> successAttributes,
                    final List<PHHueError> errorAttributes) {
                sendResultOK(response);
            }
        });
        return false;
    }

    @Override
    protected boolean onPutLightGroup(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        String groupId = getGroupId(request);

        // 必須パラメータの存在チェック
        if (groupId == null || groupId.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "groupId is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }
        String name = getName(request);
        if (name == null || name.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "name is not specified.");
            return true;
        }
        PHGroup group = getGroup(bridge, groupId);
        if (group == null) {
            MessageUtils.setUnknownError(response, "Not found group: " + groupId);
            return true;
        }

        PHGroup newGroup = new PHGroup(name, group.getIdentifier());
        bridge.updateGroup(newGroup, new PHGroupAdapter() {
            @Override
            public void onSuccess() {
                sendResultOK(response);
            }

            @Override
            public void onError(final int code, final String message) {
                String errMsg = "グループの名称変更に失敗しました hue:code = " + Integer.toString(code) + "  message = " + message;
                MessageUtils.setUnknownError(response, errMsg);
                sendResultERR(response);
            }
        });
        return false;
    }

    @Override
    protected boolean onPostLightGroupCreate(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        String groupName = getGroupName(request);
        String lightIds = getLightIds(request);

        // 必須パラメータの存在チェック
        if (groupName == null || groupName.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "groupName is not specified.");
            return true;
        }
        if (lightIds == null || lightIds.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "lightIds is not specified.");
            return true;
        }
        String[] lightIdsArray = getSelectedIdList(lightIds);
        if (lightIdsArray == null) {
            MessageUtils.setInvalidRequestParameterError(response, "lightIds is not specified.");
            return true;
        } else if (lightIdsArray.length < 1) {
            MessageUtils.setInvalidRequestParameterError(response, "lightIds is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }

        bridge.createGroup(groupName, Arrays.asList(lightIdsArray), new PHGroupAdapter() {
            @Override
            public void onCreated(final PHGroup group) {
                response.putExtra(PARAM_GROUP_ID, group.getIdentifier());
                sendResultOK(response);
            }

            @Override
            public void onError(final int code, final String msg) {
                String errMsg = "グループ作成に失敗しました hue:code = " + Integer.toString(code) + "  message = " + msg;
                if (code == HUE_SDK_ERROR_301) {
                    MessageUtils.setUnknownError(response, "グループが作成できる上限に達しています");
                } else {
                    MessageUtils.setUnknownError(response, errMsg);
                }
                sendResultERR(response);
            }
        });
        return false;
    }

    @Override
    protected boolean onDeleteLightGroupClear(final Intent request, final Intent response) {
        String serviceId = getServiceID(request);
        String groupId = getGroupId(request);

        // 必須パラメータの存在チェック
        if (groupId == null || groupId.length() == 0) {
            MessageUtils.setInvalidRequestParameterError(response, "groupId is not specified.");
            return true;
        }

        PHBridge bridge = findBridge(serviceId);
        if (bridge == null) {
            MessageUtils.setNotFoundServiceError(response, "Not found bridge: " + serviceId);
            return true;
        }

        bridge.deleteGroup(groupId, new PHGroupAdapter() {
            @Override
            public void onSuccess() {
                sendResultOK(response);
            }

            @Override
            public void onError(final int code, final String msg) {
                String errMsg = "グループ削除に失敗しました hue:code = " + Integer.toString(code) + "  message = " + msg;
                MessageUtils.setUnknownError(response, errMsg);
                sendResultERR(response);
            }
        });
        return false;
    }

    /**
     * Hueのブリッジを検索する.
     * 
     * @param serviceId Service ID
     * @return Hueのブリッジを管理するオブジェクト
     */
    private PHBridge findBridge(final String serviceId) {
        PHBridge bridge = PHHueSDK.getInstance().getSelectedBridge();
        if (bridge != null) {
            PHBridgeResourcesCache cache = bridge.getResourceCache();
            String ipAddress = cache.getBridgeConfiguration().getIpAddress();
            if (serviceId.equals(ipAddress)) {
                return bridge;
            }
        }
        return null;
    }

    /**
     * Hue Lightを管理するオブジェクトを取得する.
     * 
     * @param bridge Hueのブリッジ
     * @param lightId Light ID
     * @return Lightを管理するオブジェクト
     */
    private PHLight getLight(final PHBridge bridge, final String lightId) {
        for (PHLight light : bridge.getResourceCache().getAllLights()) {
            if (light.getIdentifier().equals(lightId)) {
                return light;
            }
        }
        return null;
    }

    /**
     * Hue Lightのグループ情報を管理するオブジェクトを取得する.
     * 
     * @param bridge Hueのブリッジ
     * @param groupID Group ID
     * @return Lightのグループ情報を持つオブジェクト
     */
    private PHGroup getGroup(final PHBridge bridge, final String groupID) {
        for (PHGroup group : bridge.getResourceCache().getAllGroups()) {
            if (groupID.equals(group.getIdentifier())) {
                return group;
            }
        }
        return null;
    }

    /**
     * 選択したLight IDのリストを取得する.
     * 
     * @param lightIdList Light IDをカンマ区切りした文字列
     * @return Light IDのリスト
     */
    private String[] getSelectedIdList(final String lightIdList) {
        String[] strAry = lightIdList.split(",");
        int i = 0;
        for (String string : strAry) {
            strAry[i] = string.trim();
            i++;
        }
        return strAry;
    }

    /**
     * Error レスポンス設定.
     * 
     * @param response response
     */
    private void setResultERR(final Intent response) {
        setResult(response, DConnectMessage.RESULT_ERROR);
    }

    /**
     * 成功レスポンス送信.
     * 
     * @param response response
     */
    private void sendResultOK(final Intent response) {
        setResult(response, DConnectMessage.RESULT_OK);
        getContext().sendBroadcast(response);
    }

    /**
     * エラーレスポンスを送信する.
     * 
     * @param response エラーレスポンス
     */
    private void sendResultERR(final Intent response) {
        setResultERR(response);
        getContext().sendBroadcast(response);
    }

    /**
     * ライトID取得.
     * 
     * @param request request
     * @return lightid
     */
    private static String getLightID(final Intent request) {
        return request.getStringExtra(PARAM_LIGHT_ID);
    }

    /**
     * 名前取得.
     * 
     * @param request request
     * @return myName
     */
    private static String getName(final Intent request) {
        return request.getStringExtra(PARAM_NAME);
    }

    /**
     * グループ名取得.
     * 
     * @param request request
     * @return myName
     */
    private static String getGroupName(final Intent request) {
        return request.getStringExtra(PARAM_GROUP_NAME);
    }

    /**
     * ライトID取得.
     * 
     * @param request request
     * @return myName
     */
    private static String getLightIds(final Intent request) {
        return request.getStringExtra(PARAM_LIGHT_IDS);
    }

    /**
     * グループID取得.
     * 
     * @param request request
     * @return myName
     */
    private static String getGroupId(final Intent request) {
        return request.getStringExtra(PARAM_GROUP_ID);
    }

    /**
     * 輝度取得.
     * 
     * @param request request
     * @return PARAM_BRIGHTNESS
     */
    private static String getBrightness(final Intent request) {
        return request.getStringExtra(PARAM_BRIGHTNESS);
    }

    /**
     * リクエストからcolorパラメータを取得する.
     * 
     * @param request リクエスト
     * @return colorパラメータ
     */
    private static String getColor(final Intent request) {
        return request.getStringExtra(PARAM_COLOR);
    }

    /**
     * Get brightness parameter.
     * 
     * @param request request
     * @param response response
     * @return Brightness parameter, if -1, parameter error.
     */
    private static float getBrightnessParam(final Intent request, final Intent response) {
        float brightness = 0;
        if (getBrightness(request) != null) {
            try {
                brightness = Float.valueOf(getBrightness(request));
                if (brightness > 1.0 || brightness < 0) {
                    MessageUtils.setInvalidRequestParameterError(response,
                            "brightness should be a value between 0 and 1.0");
                    return -1;
                }
            } catch (NumberFormatException e) {
                MessageUtils
                        .setInvalidRequestParameterError(response, "brightness should be a value between 0 and 1.0");
                return -1;
            }
        } else {
            brightness = 1;
        }
        return brightness;
    }

    /**
     * Get color parameter.
     * 
     * @param request request
     * @param response response
     * @param color Color parameter.
     * @return true : Success, false : failure.
     */
    private static boolean getColorParam(final Intent request, final Intent response, final int[] color) {
        if (getColor(request) != null) {
            try {
                String colorParam = getColor(request);
                String rr = colorParam.substring(0, 2);
                String gg = colorParam.substring(2, 4);
                String bb = colorParam.substring(4, 6);
                if (colorParam.length() == RGB_LENGTH) {
                    if (rr == null || gg == null || bb == null) {
                        MessageUtils.setInvalidRequestParameterError(response);
                        return false;
                    }
                    color[0] = Integer.parseInt(rr, 16);
                    color[1] = Integer.parseInt(gg, 16);
                    color[2] = Integer.parseInt(bb, 16);
                } else {
                    MessageUtils.setInvalidRequestParameterError(response);
                    return false;
                }
            } catch (NumberFormatException e) {
                MessageUtils.setInvalidRequestParameterError(response);
                return false;
            } catch (IllegalArgumentException e) {
                MessageUtils.setInvalidRequestParameterError(response);
                return false;
            }
        } else {
            color[0] = 0xFF;
            color[1] = 0xFF;
            color[2] = 0xFF;
        }
        return true;
    }

    /**
     * Calculate color parameter.
     * 
     * @param color Color parameters.
     * @param brightness Brightness parameter.
     * @return Color parameter.
     */
    private static String calcColorParam(final int[] color, final float brightness) {
        color[0] = Math.round(color[0] * brightness);
        color[1] = Math.round(color[1] * brightness);
        color[2] = Math.round(color[2] * brightness);

        String colorParam = "";
        for (int i = 0; i < 3; i++) {
            if (color[i] <= 0xf) {
                colorParam = colorParam + "0" + Integer.toHexString(color[i]); 
            } else {
                colorParam = colorParam + Integer.toHexString(color[i]); 
            }
        }
        return colorParam;
    }

    /** hue minimum brightness value. */
    static final int HUE_BRIGHTNESS_MIN_VALUE = 1;
    /** hue maximum brightness value. */
    static final int HUE_BRIGHTNESS_MAX_VALUE = 255;
    /** hue SDK maximum brightness value. */
    static final int HUE_BRIGHTNESS_TUNED_MAX_VALUE = 254;

    /**
     * Calculate brightness parameter.
     * 
     * @param color Color parameters.
     * @return brightness Brightness parameter.
     */
    private static int calcBrightnessParam(final int[] color) {
        int brightness = Math.max(color[0], color[1]);
        brightness = Math.max(brightness, color[2]);
        if (brightness < HUE_BRIGHTNESS_MIN_VALUE) {
            brightness = HUE_BRIGHTNESS_MIN_VALUE;
        } else if (brightness >= HUE_BRIGHTNESS_MAX_VALUE) {
            brightness = HUE_BRIGHTNESS_TUNED_MAX_VALUE;
        }
        return brightness;
    }

    /**
     * Hueの色指定.
     * 
     * @author NTT DOCOMO, INC.
     */
    private static class Color {
        /**
         * モデル.
         */
        private static final String MODEL = "LCT001";
        /** R. */
        final int mR;
        /** G. */
        final int mG;
        /** B. */
        final int mB;
        /** 色相のX座標. */
        final float mX;
        /** 色相のY座標. */
        final float mY;

        /**
         * コンストラクタ.
         * 
         * @param rgb RGBの文字列
         */
        Color(final String rgb) {
            if (rgb == null) {
                throw new IllegalArgumentException();
            }
            if (rgb.length() != RGB_LENGTH) {
                throw new IllegalArgumentException();
            }
            String rr = rgb.substring(0, 2);
            String gg = rgb.substring(2, 4);
            String bb = rgb.substring(4, 6);
            if (rr == null) {
                throw new IllegalArgumentException();
            }
            if (gg == null) {
                throw new IllegalArgumentException();
            }
            if (bb == null) {
                throw new IllegalArgumentException();
            }
            mR = Integer.parseInt(rr, 16);
            mG = Integer.parseInt(gg, 16);
            mB = Integer.parseInt(bb, 16);
            float[] xy = PHUtilities.calculateXYFromRGB(mR, mG, mB, MODEL);
            mX = xy[0];
            mY = xy[1];
        }
    }

    /**
     * ライトのアダプター.
     * 
     * @author NTT DOCOMO, INC.
     * 
     */
    private static class PHLightAdapter implements PHLightListener {

        @Override
        public void onError(final int code, final String message) {
        }

        @Override
        public void onStateUpdate(final Map<String, String> successAttribute, final List<PHHueError> errorAttribute) {
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onReceivingLightDetails(final PHLight light) {
        }

        @Override
        public void onReceivingLights(final List<PHBridgeResource> lights) {
        }

        @Override
        public void onSearchComplete() {
        }
    }

    /**
     * ライトグループのアダプター.
     * 
     * @author NTT DOCOMO, INC.
     * 
     */
    private static class PHGroupAdapter implements PHGroupListener {
        @Override
        public void onError(final int code, final String msg) {
        }

        @Override
        public void onStateUpdate(final Map<String, String> arg0, final List<PHHueError> arg1) {
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onCreated(final PHGroup group) {
        }

        @Override
        public void onReceivingAllGroups(final List<PHBridgeResource> arg0) {
        }

        @Override
        public void onReceivingGroupDetails(final PHGroup arg0) {
        }
    }
}
