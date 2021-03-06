/*
 TestPhoneProfile.java
 Copyright (c) 2014 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.test.profile;

import org.deviceconnect.android.message.MessageUtils;
import org.deviceconnect.android.profile.PhoneProfile;
import org.deviceconnect.message.DConnectMessage;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

/**
 * JUnit用テストデバイスプラグイン、Phoneプロファイル.
 * 
 * @author NTT DOCOMO, INC.
 */
public class TestPhoneProfile extends PhoneProfile {

    /**
     * スマートフォンの通話状態.
     */
    public static final CallState STATE = CallState.FINISHED; // 通話終了

    /**
     * サービスIDをチェックする.
     * 
     * @param serviceId サービスID
     * @return <code>serviceId</code>がテスト用サービスIDに等しい場合はtrue、そうでない場合はfalse
     */
    private boolean checkserviceId(final String serviceId) {
        return TestServiceDiscoveryProfile.SERVICE_ID.equals(serviceId);
    }

    /**
     * サービスIDが空の場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createEmptyserviceId(final Intent response) {
        MessageUtils.setEmptyServiceIdError(response);
    }

    /**
     * セッションキーが空の場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createEmptySessionKey(final Intent response) {
        MessageUtils.setInvalidRequestParameterError(response);
    }

    /**
     * デバイスが発見できなかった場合のエラーを作成する.
     * 
     * @param response レスポンスを格納するIntent
     */
    private void createNotFoundService(final Intent response) {
        MessageUtils.setNotFoundServiceError(response);
    }

    @Override
    protected boolean onPostCall(final Intent request, final Intent response, final String serviceId,
            final String phoneNumber) {

        if (serviceId == null) {
            createNotFoundService(response);
        } else if (!checkserviceId(serviceId)) {
            createEmptyserviceId(response);
        } else if (TextUtils.isEmpty(phoneNumber)) {
            MessageUtils.setInvalidRequestParameterError(response);
        } else {
            setResult(response, DConnectMessage.RESULT_OK);
        }

        return true;
    }

    @Override
    protected boolean onPutSet(final Intent request, final Intent response,
                                    final String serviceId, final PhoneMode mode) {

        if (serviceId == null) {
            createNotFoundService(response);
        } else if (!checkserviceId(serviceId)) {
            createEmptyserviceId(response);
        } else if (mode == null || mode == PhoneMode.UNKNOWN) {
            MessageUtils.setInvalidRequestParameterError(response);
        } else {
            setResult(response, DConnectMessage.RESULT_OK);
        }

        return true;
    }

    @Override
    protected boolean onPutOnConnect(final Intent request, final Intent response, final String serviceId,
            final String sessionKey) {

        if (serviceId == null) {
            createEmptyserviceId(response);
        } else if (!checkserviceId(serviceId)) {
            createNotFoundService(response);
        } else if (sessionKey == null) {
            createEmptySessionKey(response);
        } else {
            setResult(response, DConnectMessage.RESULT_OK);

            Intent message = MessageUtils.createEventIntent();
            setSessionKey(message, sessionKey);
            setServiceID(message, serviceId);
            setProfile(message, getProfileName());
            setAttribute(message, ATTRIBUTE_ON_CONNECT);
            Bundle phoneStatus = new Bundle();
            setPhoneNumber(phoneStatus, "090xxxxxxxx");
            setState(phoneStatus, STATE);
            setPhoneStatus(message, phoneStatus);
            Util.sendBroadcast(getContext(), message);
        }

        return true;
    }

    @Override
    protected boolean onDeleteOnConnect(final Intent request, final Intent response, final String serviceId,
            final String sessionKey) {
        if (serviceId == null) {
            createEmptyserviceId(response);
        } else if (!checkserviceId(serviceId)) {
            createNotFoundService(response);
        } else if (sessionKey == null) {
            createEmptySessionKey(response);
        } else {
            setResult(response, DConnectMessage.RESULT_OK);
        }

        return true;
    }

}
