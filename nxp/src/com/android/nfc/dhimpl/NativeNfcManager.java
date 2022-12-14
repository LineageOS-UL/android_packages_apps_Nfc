/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.nfc.dhimpl;

import com.android.nfc.DeviceHost;
import com.android.nfc.LlcpException;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.ErrorCodes;
import android.nfc.tech.Ndef;
import android.nfc.tech.TagTechnology;
import android.util.Log;
import com.android.nfc.NfcDiscoveryParameters;

import java.io.File;
import java.io.FileDescriptor;

/**
 * Native interface to the NFC Manager functions
 */
public class NativeNfcManager implements DeviceHost {
    private static final String TAG = "NativeNfcManager";

    private static final String NFC_CONTROLLER_FIRMWARE_FILE_NAME = "/vendor/firmware/libpn544_fw.so";

    static final String PREF = "NxpDeviceHost";

    private static final String PREF_FIRMWARE_MODTIME = "firmware_modtime";
    private static final long FIRMWARE_MODTIME_DEFAULT = -1;

    static final String DRIVER_NAME = "nxp";

    static final int DEFAULT_LLCP_MIU = 128;
    static final int DEFAULT_LLCP_RWSIZE = 1;

    static {
        System.loadLibrary("nfc_jni");
    }

    /* Native structure */
    private long mNative;

    private final DeviceHostListener mListener;
    private final Context mContext;

    public NativeNfcManager(Context context, DeviceHostListener listener) {
        mListener = listener;
        initializeNativeStructure();
        mContext = context;
    }

    public native boolean initializeNativeStructure();

    private native boolean doDownload();

    public native int doGetLastError();

    @Override
    public boolean checkFirmware() {
        // Check that the NFC controller firmware is up to date.  This
        // ensures that firmware updates are applied in a timely fashion,
        // and makes it much less likely that the user will have to wait
        // for a firmware download when they enable NFC in the settings
        // app.  Firmware download can take some time, so this should be
        // run in a separate thread.

        // check the timestamp of the firmware file
        File firmwareFile;
        int nbRetry = 0;
        try {
            firmwareFile = new File(NFC_CONTROLLER_FIRMWARE_FILE_NAME);
        } catch(NullPointerException npe) {
            Log.e(TAG,"path to firmware file was null");
            return false;
        }

        long modtime = firmwareFile.lastModified();

        SharedPreferences prefs = mContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long prev_fw_modtime = prefs.getLong(PREF_FIRMWARE_MODTIME, FIRMWARE_MODTIME_DEFAULT);
        Log.d(TAG,"prev modtime: " + prev_fw_modtime);
        Log.d(TAG,"new modtime: " + modtime);
        if (prev_fw_modtime == modtime) {
            return true;
        }

        // FW download.
        while(nbRetry < 5) {
            Log.d(TAG,"Perform Download");
            if(doDownload()) {
                Log.d(TAG,"Download Success");
                // Now that we've finished updating the firmware, save the new modtime.
                prefs.edit().putLong(PREF_FIRMWARE_MODTIME, modtime).apply();
                return true;
            } else {
                Log.d(TAG,"Download Failed");
                nbRetry++;
            }
        }

        return false;
    }

    private native boolean doInitialize();

    @Override
    public boolean initialize() {
        return doInitialize();
    }

    private native void doEnableDtaMode();

    @Override
    public void enableDtaMode() {
        doEnableDtaMode();
    }

    private native void doDisableDtaMode();

    @Override
    public void disableDtaMode() {
        Log.d(TAG,"disableDtaMode : entry");
        doDisableDtaMode();
    }

    private native void doFactoryReset();

    @Override
    public void factoryReset() {
        doFactoryReset();
    }

    private native void doShutdown();

    @Override
    public void shutdown() {
        doShutdown();
    }

    private native boolean doDeinitialize();

    @Override
    public boolean deinitialize() {
        return doDeinitialize();
    }

    @Override
    public String getName() {
        return DRIVER_NAME;
    }

    @Override
    public boolean sendRawFrame(byte[] data) {
        return false;
    }

    @Override
    public boolean routeAid(byte[] aid, int route, int aidInfo, int power) {
        return false;
    }

    @Override
    public boolean unrouteAid(byte[] aid) {
       return false;
    }

    @Override
    public boolean commitRouting() {
        return false;
    }

    @Override
    public void registerT3tIdentifier(byte[] t3tIdentifier) {
        return;
    }

    @Override
    public void deregisterT3tIdentifier(byte[] t3tIdentifier) {
        return;
    }

    @Override
    public void clearT3tIdentifiersCache() {
        return;
    }

    @Override
    public int getLfT3tMax() {
        return 0;
    }

    @Override
    public native void doSetScreenState(int screen_state_mask);

    @Override
    public native int getNciVersion();

    private native void doEnableDiscovery(int techMask,
                                          boolean enableLowPowerPolling,
                                          boolean enableReaderMode,
                                          boolean enableP2p,
                                          boolean restart);
    @Override
    public void enableDiscovery(NfcDiscoveryParameters params, boolean restart) {
        doEnableDiscovery(params.getTechMask(), params.shouldEnableLowPowerDiscovery(),
                params.shouldEnableReaderMode(), params.shouldEnableP2p(), restart);
    }

    @Override
    public native void disableDiscovery();

    private native NativeLlcpConnectionlessSocket doCreateLlcpConnectionlessSocket(int nSap,
            String sn);

    @Override
    public LlcpConnectionlessSocket createLlcpConnectionlessSocket(int nSap, String sn)
            throws LlcpException {
        LlcpConnectionlessSocket socket = doCreateLlcpConnectionlessSocket(nSap, sn);
        if (socket != null) {
            return socket;
        } else {
            /* Get Error Status */
            int error = doGetLastError();

            Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));

            switch (error) {
                case ErrorCodes.ERROR_BUFFER_TO_SMALL:
                case ErrorCodes.ERROR_INSUFFICIENT_RESOURCES:
                    throw new LlcpException(error);
                default:
                    throw new LlcpException(ErrorCodes.ERROR_SOCKET_CREATION);
            }
        }
    }

    private native NativeLlcpServiceSocket doCreateLlcpServiceSocket(int nSap, String sn, int miu,
            int rw, int linearBufferLength);
    @Override
    public LlcpServerSocket createLlcpServerSocket(int nSap, String sn, int miu,
            int rw, int linearBufferLength) throws LlcpException {
        LlcpServerSocket socket = doCreateLlcpServiceSocket(nSap, sn, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        } else {
            /* Get Error Status */
            int error = doGetLastError();

            Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));

            switch (error) {
                case ErrorCodes.ERROR_BUFFER_TO_SMALL:
                case ErrorCodes.ERROR_INSUFFICIENT_RESOURCES:
                    throw new LlcpException(error);
                default:
                    throw new LlcpException(ErrorCodes.ERROR_SOCKET_CREATION);
            }
        }
    }

    private native NativeLlcpSocket doCreateLlcpSocket(int sap, int miu, int rw,
            int linearBufferLength);
    @Override
    public LlcpSocket createLlcpSocket(int sap, int miu, int rw,
            int linearBufferLength) throws LlcpException {
        LlcpSocket socket = doCreateLlcpSocket(sap, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        } else {
            /* Get Error Status */
            int error = doGetLastError();

            Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));

            switch (error) {
                case ErrorCodes.ERROR_BUFFER_TO_SMALL:
                case ErrorCodes.ERROR_INSUFFICIENT_RESOURCES:
                    throw new LlcpException(error);
                default:
                    throw new LlcpException(ErrorCodes.ERROR_SOCKET_CREATION);
            }
        }
    }

    @Override
    public native boolean doCheckLlcp();

    @Override
    public native boolean doActivateLlcp();

    private native void doResetTimeouts();

    @Override
    public void resetTimeouts() {
        doResetTimeouts();
    }

    @Override
    public native void doAbort(String msg);

    private native boolean doSetTimeout(int tech, int timeout);
    @Override
    public boolean setTimeout(int tech, int timeout) {
        return doSetTimeout(tech, timeout);
    }

    private native int doGetTimeout(int tech);
    @Override
    public int getTimeout(int tech) {
        return doGetTimeout(tech);
    }


    @Override
    public boolean canMakeReadOnly(int ndefType) {
        return (ndefType == Ndef.TYPE_1 || ndefType == Ndef.TYPE_2 ||
                ndefType == Ndef.TYPE_MIFARE_CLASSIC);
    }

    @Override
    public int getMaxTransceiveLength(int technology) {
        switch (technology) {
            case (TagTechnology.NFC_A):
            case (TagTechnology.MIFARE_CLASSIC):
            case (TagTechnology.MIFARE_ULTRALIGHT):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.NFC_B):
                return 0; // PN544 does not support transceive of raw NfcB
            case (TagTechnology.NFC_V):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.ISO_DEP):
                /* The maximum length of a normal IsoDep frame consists of:
                 * CLA, INS, P1, P2, LC, LE + 255 payload bytes = 261 bytes
                 * such a frame is supported. Extended length frames however
                 * are not supported.
                 */
                return 261; // Will be automatically split in two frames on the RF layer
            case (TagTechnology.NFC_F):
                return 252; // PN544 RF buffer = 255 bytes, subtract one for SoD, two for CRC
            default:
                return 0;
        }

    }

    @Override
    public int getAidTableSize() {
        return 0;
    }

    @Override
    public String getNfaStorageDir() {
        return mContext.getFilesDir().getAbsolutePath();
    }

    private native void doStartStopPolling(boolean start);
    @Override
    public void startStopPolling(boolean start) {
        doStartStopPolling(start);
    }

    private native void doSetNfceePowerAndLinkCtrl(boolean enable);
    @Override
    public void setNfceePowerAndLinkCtrl(boolean enable) {
        doSetNfceePowerAndLinkCtrl(enable);
    }

    @Override
    public native byte[] getRoutingTable();

    @Override
    public native int getMaxRoutingTableSize();

    private native void doSetP2pInitiatorModes(int modes);
    @Override
    public void setP2pInitiatorModes(int modes) {
        doSetP2pInitiatorModes(modes);
    }

    private native void doSetP2pTargetModes(int modes);
    @Override
    public void setP2pTargetModes(int modes) {
        doSetP2pTargetModes(modes);
    }

    @Override
    public boolean enableScreenOffSuspend() {
        // Snooze mode not supported on NXP silicon
        Log.i(TAG, "Snooze mode is not supported on NXP NFCC");
        return false;
    }

    @Override
    public boolean disableScreenOffSuspend() {
        // Snooze mode not supported on NXP silicon
        Log.i(TAG, "Snooze mode is not supported on NXP NFCC");
        return true;
    }

    @Override
    public boolean getExtendedLengthApdusSupported() {
        // Not supported on the PN544
        return false;
    }

    @Override
    public int getDefaultLlcpMiu() {
        return DEFAULT_LLCP_MIU;
    }

    @Override
    public int getDefaultLlcpRwSize() {
        return DEFAULT_LLCP_RWSIZE;
    }

    private native void doDump(FileDescriptor fd);
    @Override
    public void dump(FileDescriptor fd) {
        doDump(fd);
    }

    @Override
    public boolean setNfcSecure(boolean enable) {
        return true;
    }

    /**
     * Notifies Ndef Message (TODO: rename into notifyTargetDiscovered)
     */
    private void notifyNdefMessageListeners(NativeNfcTag tag) {
        mListener.onRemoteEndpointDiscovered(tag);
    }

    /**
     * Notifies P2P Device detected, to activate LLCP link
     */
    private void notifyLlcpLinkActivation(NativeP2pDevice device) {
        mListener.onLlcpLinkActivated(device);
    }

    /**
     * Notifies P2P Device detected, to activate LLCP link
     */
    private void notifyLlcpLinkDeactivated(NativeP2pDevice device) {
        mListener.onLlcpLinkDeactivated(device);
    }

    private void notifyHostEmuActivated(int technology) {
        mListener.onHostCardEmulationActivated(technology);
    }

    private void notifyHostEmuData(int technology, byte[] data) {
        mListener.onHostCardEmulationData(technology, data);
    }

    private void notifyHostEmuDeactivated(int technology) {
        mListener.onHostCardEmulationDeactivated(technology);
    }

    private void notifyRfFieldActivated() {
        mListener.onRemoteFieldActivated();
    }

    private void notifyRfFieldDeactivated() {
        mListener.onRemoteFieldDeactivated();
    }
}
