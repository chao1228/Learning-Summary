/*
 * Copyright (C) 2010 The Android Open Source Project
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
 *
 * Copyright (C) 2018 BlackBerry Limited. All rights reserved.
 */

package com.android.server.connectivity;

import static android.hardware.usb.UsbManager.USB_CONFIGURED;
import static android.hardware.usb.UsbManager.USB_CONNECTED;
import static android.hardware.usb.UsbManager.USB_FUNCTION_RNDIS;
import static android.net.ConnectivityManager.getNetworkTypeName;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_MODE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_CONFIGURATION_ERROR;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_LOCAL_ONLY;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_TETHERED;
import static android.net.wifi.WifiManager.IFACE_IP_MODE_UNSPECIFIED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static com.android.server.ConnectivityService.SHORT_ARG;
// start:BBSECURE_TETH_TO
import android.app.AlarmManager;
// end:BBSECURE_TETH_TO
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
// start:BBSECURE_TETH_TO
import android.content.ContentResolver;
// end:BBSECURE_TETH_TO
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.INetworkPolicyManager;
import android.net.INetworkStatsService;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.NetworkState;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.util.PrefixUtils;
import android.net.util.SharedLog;
// start:BBSECURE_TETH_TO
import android.net.wifi.WifiDevice;
// end:BBSECURE_TETH_TO
import android.net.wifi.WifiManager;
// start:BBSECURE_TETH_TO
import android.os.BatteryManager;
// end:BBSECURE_TETH_TO
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ResultReceiver;
// start:BBRY_ANDROID
import android.os.UserHandle;
import android.os.SystemClock;
// end:BBRY_ANDROID
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.UserManagerInternal;
import android.os.UserManagerInternal.UserRestrictionsListener;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.LocalServices;
import com.android.server.connectivity.tethering.IControlsTethering;
import com.android.server.connectivity.tethering.IPv6TetheringCoordinator;
import com.android.server.connectivity.tethering.OffloadController;
import com.android.server.connectivity.tethering.SimChangeListener;
import com.android.server.connectivity.tethering.TetherInterfaceStateMachine;
import com.android.server.connectivity.tethering.TetheringConfiguration;
import com.android.server.connectivity.tethering.TetheringDependencies;
import com.android.server.connectivity.tethering.UpstreamNetworkMonitor;
import com.android.server.net.BaseNetworkObserver;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
//[FEATURE]-Add-BEGIN by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
import android.net.IConnectivityManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.view.WindowManager;
//[FEATURE]-Add-END by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement

import android.app.admin.DevicePolicyManager;

/**
 * @hide
 *
 * This class holds much of the business logic to allow Android devices
 * to act as IP gateways via USB, BT, and WiFi interfaces.
 */
public class Tethering extends BaseNetworkObserver {

    private final static String TAG = Tethering.class.getSimpleName();
    private final static boolean DBG = false;
    private final static boolean VDBG = false;

    protected static final String DISABLE_PROVISIONING_SYSPROP_KEY = "net.tethering.noprovisioning";

    private static final Class[] messageClasses = {
            Tethering.class, TetherMasterSM.class, TetherInterfaceStateMachine.class
    };
    private static final SparseArray<String> sMagicDecoderRing =
            MessageUtils.findMessageNames(messageClasses);

    // {@link ComponentName} of the Service used to run tether provisioning.
    private static final ComponentName TETHER_SERVICE = ComponentName.unflattenFromString(Resources
            .getSystem().getString(com.android.internal.R.string.config_wifi_tether_enable));

    private static class TetherState {
        public final TetherInterfaceStateMachine stateMachine;
        public int lastState;
        public int lastError;

        public TetherState(TetherInterfaceStateMachine sm) {
            stateMachine = sm;
            // Assume all state machines start out available and with no errors.
            lastState = IControlsTethering.STATE_AVAILABLE;
            lastError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        }

        public boolean isCurrentlyServing() {
            switch (lastState) {
                case IControlsTethering.STATE_TETHERED:
                case IControlsTethering.STATE_LOCAL_ONLY:
                    return true;
                default:
                    return false;
            }
        }
    }

    private final SharedLog mLog = new SharedLog(TAG);

    // used to synchronize public access to members
    private final Object mPublicSync;
    private final Context mContext;
    private final ArrayMap<String, TetherState> mTetherStates;
    private final BroadcastReceiver mStateReceiver;
    private final INetworkManagementService mNMService;
    private final INetworkStatsService mStatsService;
    private final INetworkPolicyManager mPolicyManager;
    private final Looper mLooper;
    private final MockableSystemProperties mSystemProperties;
    private final StateMachine mTetherMasterSM;
    private final OffloadController mOffloadController;
    private final UpstreamNetworkMonitor mUpstreamNetworkMonitor;
    // TODO: Figure out how to merge this and other downstream-tracking objects
    // into a single coherent structure.
    private final HashSet<TetherInterfaceStateMachine> mForwardedDownstreams;
    private final SimChangeListener mSimChange;

    private volatile TetheringConfiguration mConfig;
    private String mCurrentUpstreamIface;
    private Notification.Builder mTetheredNotificationBuilder;
    private int mLastNotificationId;

    private boolean mRndisEnabled;       // track the RNDIS function enabled state
    private boolean mUsbTetherRequested; // true if USB tethering should be started
                                         // when RNDIS is enabled
    // True iff. WiFi tethering should be started when soft AP is ready.
    private boolean mWifiTetherRequested;
    private boolean v6OnlyTetherEnabled;


    /* MODIFIED-BEGIN by zhouchenglin, 2017-11-03,BUG-5558976*/
    //[FEATURE]-Add-BEGIN by chenglin.zhou, service layer poliy entitlement.
    public static final String ACTION_SERVICE_ENTITLEMENT = "action.service.entitlement";
    public static final String ACTION_SERVICE_ENTITLEMENT_RESULT = "action.service.entitlement.result";
    public static final String CHECK_IFACE = "check.iface";
    public static final String CHECK_RESULT = "check.result";
    public static final long WAIT_CHECK_TIME = 3000;
    public static final long CHECK_CIRCLE = 5;
    private static final String PROPERTY_ECID = "ro.ecid";
    /* MODIFIED-BEGIN by zhouchenglin, 2017-12-08,BUG-5719037*/
    public static final String ACTION_CONNECTIVITY_APN_FAIL = "action.connectivity.apn.failed";
    public static boolean mUsbTetherFlag = false;
    public static boolean mHotSpotTetherFlag = false;
    public static boolean isAtt = false;
    /* MODIFIED-END by zhouchenglin,BUG-5719037*/

    private boolean mSPETetherUsbPassed = false;
    private boolean mSPETetherBluetoothPassed = false;
    private boolean mSPEMHSPassed = false;
    private boolean mTetherUsbPermissionSet = false;
    private boolean mTetherBluetoothPermissionSet = false;
    private boolean mMHSPermissionSet = false;
    private boolean mSPETetherBluetoothDunPassed = false;
    private boolean mTetherBluetoothDunPermissionSet = false;

  //[FEATURE]-Add-END by chenglin.zhou, service layer poliy entitlement.
  /* MODIFIED-END by zhouchenglin,BUG-5558976*/
    // start:BBSECURE_TETH_TO
    // Fields used to manage the inactivity timer for MHS tethering.

    // Inactivity time in minutes to wait before automatically shutting down
    // inactive MHS tethering when not connected to external power.
    // A value of 0 or less than 0 indicates to never automatically shut down
    // MHS tethering.
    private long mMhsInactivityTimeout = 10;

    // The flag indicates if the device is connected to external power
    private boolean mDeviceHasExternalPower = false;

    // The notification when MHS session terminates due to inactivity timeout
    private Notification.Builder mMhsInactivityNotification = null;
    private static final int MHS_TIME_OUT_ICON = com.android.internal.R.drawable.stat_sys_tether_wifi;

    // TAGs for notifications
    private final String TAG_TETHERED_NOTIFICATION = "TetheredNotification";
    private final String TAG_MHS_INACTIVITY_NOTIFICATION = "MHSInactivityNotification";

    // PendingIntent used for triggering MHS tethering shutdown
    // when inactive for a given amount of time
    private PendingIntent mMhsInactivityPendingIntent = null;

    // Define a new action which will terminate MHS session due to inactivity timeout
    private final String ACTION_MHS_INACTIVITY_ALARM = "MHSInactivityAlarm";
    // end:BBSECURE_TETH_TO


    public Tethering(Context context, INetworkManagementService nmService,
            INetworkStatsService statsService, INetworkPolicyManager policyManager,
            Looper looper, MockableSystemProperties systemProperties,
            TetheringDependencies deps) {
        mLog.mark("constructed");
        mContext = context;
        mNMService = nmService;
        mStatsService = statsService;
        mPolicyManager = policyManager;
        mLooper = looper;
        mSystemProperties = systemProperties;

        mPublicSync = new Object();

        mTetherStates = new ArrayMap<>();

        v6OnlyTetherEnabled = (Settings.Global.getInt(mContext.
                                         getContentResolver(),
                                         "enable_v6_only_tethering", 0) == 1);

        mTetherMasterSM = new TetherMasterSM("TetherMaster", mLooper);
        mTetherMasterSM.start();

        final Handler smHandler = mTetherMasterSM.getHandler();
        mOffloadController = new OffloadController(smHandler,
                deps.getOffloadHardwareInterface(smHandler, mLog),
                mContext.getContentResolver(), mNMService,
                mLog);
        mUpstreamNetworkMonitor = new UpstreamNetworkMonitor(
                mContext, mTetherMasterSM, mLog, TetherMasterSM.EVENT_UPSTREAM_CALLBACK);
        mForwardedDownstreams = new HashSet<>();
        mSimChange = new SimChangeListener(
                mContext, smHandler, () -> reevaluateSimCardProvisioning());

        mStateReceiver = new StateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        // start:BBSECURE_TETH_TO
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(ACTION_MHS_INACTIVITY_ALARM);

        //New in O - connect state changed broadcast defined in ConnectivityManager
        filter.addAction(ConnectivityManager.TETHER_CONNECT_STATE_CHANGED);
        // end:BBSECURE_TETH_TO

        mContext.registerReceiver(mStateReceiver, filter, null, mTetherMasterSM.getHandler());



        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        mContext.registerReceiver(mStateReceiver, filter, null, smHandler);


        //[FEATURE]-Add-BEGIN by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
        if (SystemProperties.getInt(PROPERTY_ECID,-1) == 50) {
            filter = new IntentFilter();
            filter.addAction(ACTION_SERVICE_ENTITLEMENT_RESULT);
            mContext.registerReceiver(mStateReceiver, filter, null, smHandler);
            /* MODIFIED-BEGIN by zhouchenglin, 2017-12-08,BUG-5719037*/
            filter = new IntentFilter();
            filter.addAction(ACTION_CONNECTIVITY_APN_FAIL);
            mContext.registerReceiver(mStateReceiver, filter, null, smHandler);
            isAtt = true;
            /* MODIFIED-END by zhouchenglin,BUG-5719037*/
        }
       //[FEATURE]-Add-END by chenglin.zhou

        UserManagerInternal userManager = LocalServices.getService(UserManagerInternal.class);

        // this check is useful only for some unit tests; example: ConnectivityServiceTest
        if (userManager != null) {
            userManager.addUserRestrictionsListener(new TetheringUserRestrictionListener(this));
        }

        // start:BBSECURE_TETH_TO
        BatteryManager batteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);

        // when no battery manager is available, our timer will cause bad user experience, so we disable Tethering timer by setting changing as true.
        if (batteryManager == null) {
            Log.e(TAG, "No battery manager found. Tethering timer inactive");
            mDeviceHasExternalPower = true;
        } else if (batteryManager.isCharging()) {
            mDeviceHasExternalPower = true;
        }
        // end:BBSECURE_TETH_TO

        // load device config info
        updateConfiguration();
    }

    // We can't do this once in the Tethering() constructor and cache the value, because the
    // CONNECTIVITY_SERVICE is registered only after the Tethering() constructor has completed.
    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private WifiManager getWifiManager() {
        return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    // NOTE: This is always invoked on the mLooper thread.
    private void updateConfiguration() {
        mConfig = new TetheringConfiguration(mContext, mLog);
        mUpstreamNetworkMonitor.updateMobileRequiresDun(mConfig.isDunRequired);
    }

    private void maybeUpdateConfiguration() {
        final int dunCheck = TetheringConfiguration.checkDunRequired(mContext);
        if (dunCheck == mConfig.dunCheck) return;
        updateConfiguration();
    }

    @Override
    public void interfaceStatusChanged(String iface, boolean up) {
        // Never called directly: only called from interfaceLinkStateChanged.
        // See NetlinkHandler.cpp:71.
        if (VDBG) Log.d(TAG, "interfaceStatusChanged " + iface + ", " + up);
        synchronized (mPublicSync) {
            if (up) {
                maybeTrackNewInterfaceLocked(iface);
            } else {
                if (ifaceNameToType(iface) == ConnectivityManager.TETHERING_BLUETOOTH ||
                    ifaceNameToType(iface) == ConnectivityManager.TETHERING_WIGIG) {
                    stopTrackingInterfaceLocked(iface);
                } else {
                    // Ignore usb0 down after enabling RNDIS.
                    // We will handle disconnect in interfaceRemoved.
                    // Similarly, ignore interface down for WiFi.  We monitor WiFi AP status
                    // through the WifiManager.WIFI_AP_STATE_CHANGED_ACTION intent.
                    if (VDBG) Log.d(TAG, "ignore interface down for " + iface);
                }
            }
        }
    }

    @Override
    public void interfaceLinkStateChanged(String iface, boolean up) {
        interfaceStatusChanged(iface, up);
    }

    private int ifaceNameToType(String iface) {
        final TetheringConfiguration cfg = mConfig;

        if (cfg.isWifi(iface)) {
            String wigigIface = SystemProperties.get("vendor.wigig.interface", "wigig0");
            if (wigigIface.equals(iface)) {
                return ConnectivityManager.TETHERING_WIGIG;
            }
            return ConnectivityManager.TETHERING_WIFI;
        } else if (cfg.isUsb(iface)) {
            return ConnectivityManager.TETHERING_USB;
        } else if (cfg.isBluetooth(iface)) {
            return ConnectivityManager.TETHERING_BLUETOOTH;
        }
        return ConnectivityManager.TETHERING_INVALID;
    }

    @Override
    public void interfaceAdded(String iface) {
        if (VDBG) Log.d(TAG, "interfaceAdded " + iface);
        synchronized (mPublicSync) {
            maybeTrackNewInterfaceLocked(iface);
        }
    }

    @Override
    public void interfaceRemoved(String iface) {
        if (VDBG) Log.d(TAG, "interfaceRemoved " + iface);
        synchronized (mPublicSync) {
            stopTrackingInterfaceLocked(iface);
        }
    }

    public void startTethering(int type, ResultReceiver receiver, boolean showProvisioningUi) {
        if (!isTetherProvisioningRequired()) {
            enableTetheringInternal(type, true, receiver);
            return;
        }

        if (showProvisioningUi) {
            runUiTetherProvisioningAndEnable(type, receiver);
        } else {
            runSilentTetherProvisioningAndEnable(type, receiver);
        }
    }

    public void stopTethering(int type) {
        enableTetheringInternal(type, false, null);
        if (isTetherProvisioningRequired()) {
            cancelTetherProvisioningRechecks(type);
        }
    }

    /**
     * Check if the device requires a provisioning check in order to enable tethering.
     *
     * @return a boolean - {@code true} indicating tether provisioning is required by the carrier.
     */
    @VisibleForTesting
    protected boolean isTetherProvisioningRequired() {
        String[] provisionApp = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app);
        /* MODIFIED-BEGIN by jie.zhang, 2018-01-29,BUG-5912877*/
        if ((SystemProperties.getInt(PROPERTY_ECID,-1) == 50)
                || mSystemProperties.getBoolean(DISABLE_PROVISIONING_SYSPROP_KEY, false)
                /* MODIFIED-END by jie.zhang,BUG-5912877*/
                || provisionApp == null) {
            return false;
        }

        // Check carrier config for entitlement checks
        final CarrierConfigManager configManager = (CarrierConfigManager) mContext
             .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null && configManager.getConfig() != null) {
            // we do have a CarrierConfigManager and it has a config.
            boolean isEntitlementCheckRequired = configManager.getConfig().getBoolean(
                    CarrierConfigManager.KEY_REQUIRE_ENTITLEMENT_CHECKS_BOOL);
            if (!isEntitlementCheckRequired) {
                return false;
            }
        }
        return (provisionApp.length == 2);
    }

    // Used by the SIM card change observation code.
    // TODO: De-duplicate above code.
    private boolean hasMobileHotspotProvisionApp() {
        try {
            if (!mContext.getResources().getString(com.android.internal.R.string.
                    config_mobile_hotspot_provision_app_no_ui).isEmpty()) {
                Log.d(TAG, "re-evaluate provisioning");
                return true;
            }
        } catch (Resources.NotFoundException e) {}
        Log.d(TAG, "no prov-check needed for new SIM");
        return false;
    }

    /**
     * Enables or disables tethering for the given type. This should only be called once
     * provisioning has succeeded or is not necessary. It will also schedule provisioning rechecks
     * for the specified interface.
     */
    private void enableTetheringInternal(int type, boolean enable, ResultReceiver receiver) {
        boolean isProvisioningRequired = enable && isTetherProvisioningRequired();
        int result;
        switch (type) {
            case ConnectivityManager.TETHERING_WIFI:
                result = setWifiTethering(enable);
                if (isProvisioningRequired && result == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    scheduleProvisioningRechecks(type);
                }
                sendTetherResult(receiver, result);
                break;
            case ConnectivityManager.TETHERING_USB:
                result = setUsbTethering(enable);
                if (isProvisioningRequired && result == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    scheduleProvisioningRechecks(type);
                }
                sendTetherResult(receiver, result);
                break;
            case ConnectivityManager.TETHERING_BLUETOOTH:
                setBluetoothTethering(enable, receiver);
                break;
            default:
                Log.w(TAG, "Invalid tether type.");
                sendTetherResult(receiver, ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE);
        }
    }

    private void sendTetherResult(ResultReceiver receiver, int result) {
        if (receiver != null) {
            receiver.send(result, null);
        }
    }

    private int setWifiTethering(final boolean enable) {
        /* MODIFIED-BEGIN by zhouchenglin, 2017-12-08,BUG-5719037*/
        if(isAtt)
            mHotSpotTetherFlag = enable;
            /* MODIFIED-END by zhouchenglin,BUG-5719037*/
        int rval = ConnectivityManager.TETHER_ERROR_MASTER_ERROR;
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mPublicSync) {
                mWifiTetherRequested = enable;
                final WifiManager mgr = getWifiManager();
                if ((enable && mgr.startSoftAp(null /* use existing wifi config */)) ||
                    (!enable && mgr.stopSoftAp())) {
                    rval = ConnectivityManager.TETHER_ERROR_NO_ERROR;
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return rval;
    }

    private void setBluetoothTethering(final boolean enable, final ResultReceiver receiver) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Tried to enable bluetooth tethering with null or disabled adapter. null: " +
                    (adapter == null));
            sendTetherResult(receiver, ConnectivityManager.TETHER_ERROR_SERVICE_UNAVAIL);
            return;
        }

        adapter.getProfileProxy(mContext, new ServiceListener() {
            @Override
            public void onServiceDisconnected(int profile) { }

            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                ((BluetoothPan) proxy).setBluetoothTethering(enable);
                // TODO: Enabling bluetooth tethering can fail asynchronously here.
                // We should figure out a way to bubble up that failure instead of sending success.
                int result = ((BluetoothPan) proxy).isTetheringOn() == enable ?
                        ConnectivityManager.TETHER_ERROR_NO_ERROR :
                        ConnectivityManager.TETHER_ERROR_MASTER_ERROR;
                sendTetherResult(receiver, result);
                if (enable && isTetherProvisioningRequired()) {
                    scheduleProvisioningRechecks(ConnectivityManager.TETHERING_BLUETOOTH);
                }
                adapter.closeProfileProxy(BluetoothProfile.PAN, proxy);
            }
        }, BluetoothProfile.PAN);
    }

    private void runUiTetherProvisioningAndEnable(int type, ResultReceiver receiver) {
        ResultReceiver proxyReceiver = getProxyReceiver(type, receiver);
        sendUiTetherProvisionIntent(type, proxyReceiver);
    }

    private void sendUiTetherProvisionIntent(int type, ResultReceiver receiver) {
        Intent intent = new Intent(Settings.ACTION_TETHER_PROVISIONING);
        intent.putExtra(ConnectivityManager.EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(ConnectivityManager.EXTRA_PROVISION_CALLBACK, receiver);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /**
     * Creates a proxy {@link ResultReceiver} which enables tethering if the provisioning result
     * is successful before firing back up to the wrapped receiver.
     *
     * @param type The type of tethering being enabled.
     * @param receiver A ResultReceiver which will be called back with an int resultCode.
     * @return The proxy receiver.
     */
    private ResultReceiver getProxyReceiver(final int type, final ResultReceiver receiver) {
        ResultReceiver rr = new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                // If provisioning is successful, enable tethering, otherwise just send the error.
                if (resultCode == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    enableTetheringInternal(type, true, receiver);
                } else {
                    sendTetherResult(receiver, resultCode);
                }
            }
        };

        // The following is necessary to avoid unmarshalling issues when sending the receiver
        // across processes.
        Parcel parcel = Parcel.obtain();
        rr.writeToParcel(parcel,0);
        parcel.setDataPosition(0);
        ResultReceiver receiverForSending = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiverForSending;
    }

    private void scheduleProvisioningRechecks(int type) {
        Intent intent = new Intent();
        intent.putExtra(ConnectivityManager.EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(ConnectivityManager.EXTRA_SET_ALARM, true);
        intent.setComponent(TETHER_SERVICE);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startServiceAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void runSilentTetherProvisioningAndEnable(int type, ResultReceiver receiver) {
        ResultReceiver proxyReceiver = getProxyReceiver(type, receiver);
        sendSilentTetherProvisionIntent(type, proxyReceiver);
    }

    private void sendSilentTetherProvisionIntent(int type, ResultReceiver receiver) {
        Intent intent = new Intent();
        intent.putExtra(ConnectivityManager.EXTRA_ADD_TETHER_TYPE, type);
        intent.putExtra(ConnectivityManager.EXTRA_RUN_PROVISION, true);
        intent.putExtra(ConnectivityManager.EXTRA_PROVISION_CALLBACK, receiver);
        intent.setComponent(TETHER_SERVICE);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.startServiceAsUser(intent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void cancelTetherProvisioningRechecks(int type) {
        if (getConnectivityManager().isTetheringSupported()) {
            Intent intent = new Intent();
            intent.putExtra(ConnectivityManager.EXTRA_REM_TETHER_TYPE, type);
            intent.setComponent(TETHER_SERVICE);
            final long ident = Binder.clearCallingIdentity();
            try {
                mContext.startServiceAsUser(intent, UserHandle.CURRENT);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    // Used by the SIM card change observation code.
    // TODO: De-duplicate with above code, where possible.
    private void startProvisionIntent(int tetherType) {
        final Intent startProvIntent = new Intent();
        startProvIntent.putExtra(ConnectivityManager.EXTRA_ADD_TETHER_TYPE, tetherType);
        startProvIntent.putExtra(ConnectivityManager.EXTRA_RUN_PROVISION, true);
        startProvIntent.setComponent(TETHER_SERVICE);
        mContext.startServiceAsUser(startProvIntent, UserHandle.CURRENT);
    }

    public int tether(String iface) {
        return tether(iface, IControlsTethering.STATE_TETHERED);
    }

 //[FEATURE]-Add-BEGIN by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
     private int tether(String iface,int requestedState) {
        if (DBG) Log.d(TAG, "Tethering " + iface);

       if (SystemProperties.getInt(PROPERTY_ECID,-1) != 50) {
            if (DBG) Log.d(TAG, "Tethering ,NOT ATT product, return tether_internel");
            return tether_internel(iface,requestedState);
        }

        boolean permit = false;
        permit = checkPermission(iface);
        if (DBG) Log.d(TAG, "permit : " + permit);
        if(!permit){
            synchronized (mPublicSync) {
                TetherState tetherState = mTetherStates.get(iface);

                if (tetherState == null) {
                    Log.e(TAG, "Tried to Tether an unknown iface: " + iface + ", ignoring");
                    return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
                }
                // Ignore the error status of the interface.  If the interface is available,
                // the errors are referring to past tethering attempts anyway.
                if (tetherState.lastState != IControlsTethering.STATE_AVAILABLE) {
                    Log.e(TAG, "Tried to Tether an unavailable iface: " + iface + ", ignoring");
                    return ConnectivityManager.TETHER_ERROR_UNAVAIL_IFACE;
                }

                tetherState.stateMachine.sendMessage(TetherInterfaceStateMachine.CMD_TETHER_REQUESTED,requestedState);
                return ConnectivityManager.TETHER_ERROR_NO_ERROR;
            }
        }else{
            return tether_internel(iface,requestedState);
        }
    }

    private int tether_internel(String iface, int requestedState) {
        if (DBG) Log.d(TAG, "tether_internel " + iface);
        synchronized (mPublicSync) {
            TetherState tetherState = mTetherStates.get(iface);
            if (tetherState == null) {
                Log.e(TAG, "Tried to Tether an unknown iface: " + iface + ", ignoring");
                return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
            }
            // Ignore the error status of the interface.  If the interface is available,
            // the errors are referring to past tethering attempts anyway.
            if (tetherState.lastState != IControlsTethering.STATE_AVAILABLE) {
                Log.e(TAG, "Tried to Tether an unavailable iface: " + iface + ", ignoring");
                return ConnectivityManager.TETHER_ERROR_UNAVAIL_IFACE;
            }
            // NOTE: If a CMD_TETHER_REQUESTED message is already in the TISM's
            // queue but not yet processed, this will be a no-op and it will not
            // return an error.
            //
            // TODO: reexamine the threading and messaging model.
            tetherState.stateMachine.sendMessage(
                    TetherInterfaceStateMachine.CMD_TETHER_REQUESTED, requestedState);
            return ConnectivityManager.TETHER_ERROR_NO_ERROR;
        }
    /* MODIFIED-BEGIN by zhouchenglin, 2017-11-03,BUG-5558976*/
    }

    //Hotspot Enablement
    private boolean isATTSimCard(){
      boolean isAttCard = false;
      String simNumeric = TelephonyManager.getDefault().getSimOperator();
      if(!TextUtils.isEmpty(simNumeric)){
         /* MODIFIED-BEGIN by jiang.li, 2018-05-07,BUG-6293613*/
         if (simNumeric.equals("311180") || simNumeric.equals("310410")
             || simNumeric.equals("310150") || simNumeric.equals("310170")
             || simNumeric.equals("310380") || simNumeric.equals("310560")
             || simNumeric.equals("310680") || simNumeric.equals("310070")
             || simNumeric.equals("313100") || simNumeric.equals("312670")
             || simNumeric.equals("310090")) {
             /* MODIFIED-END by jiang.li,BUG-6293613*/
            isAttCard = true;
         }
      }
      return isAttCard;
    }

    public boolean checkPermission(String iface){
        Tethering tether = (Tethering)this;
        //[BUGFIX]-Add-BEGIN by TCTNB.Yongzhen.wang,07/02/2015,PR-1034328,
        //[10776 DDA][A68 Wi-Fi Mobile Hotspot][GSM-BTR-5-9554]Authorization for Mobile
        //Hotspot Enablement, MHS can not be enabled when SIM is absent
        if(TelephonyManager.getDefault().hasIccCard() && (isATTSimCard() == false)){
           return true;
        }
        //[BUGFIX]-Add-END by TCTNB.Yongzhen.wang
        Log.d(TAG, "checkPermission " + iface);
        clearPermission(iface);
        //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,CR434832,04/05/2013
        int needCheck =  Settings.Secure.getInt(mContext.getContentResolver(),
                "NEED_SERVICE_ENTITLEMENT", 1);
        //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,CR434832,04/05/2013
        if(needCheck != 1)
            return true;

        IBinder b = ServiceManager.getService(Context.CONNECTIVITY_SERVICE);
        IConnectivityManager cm = IConnectivityManager.Stub.asInterface(b);
        int type = ConnectivityManager.TYPE_WIFI;
        NetworkInfo info = null;
        try {
            info = cm.getNetworkInfo(type);

        }catch (RemoteException e) { }
        if (info == null ||(info!=null && info.isConnected())) {
            return true;
        }
        Intent intent = new Intent(ACTION_SERVICE_ENTITLEMENT);
        intent.putExtra(CHECK_IFACE, iface);
        Log.d(TAG, "sendStickyBroadcastAsUser " + ACTION_SERVICE_ENTITLEMENT);
        //mContext.sendStickyBroadcast(intent);
        mContext.sendStickyBroadcastAsUser(intent,UserHandle.ALL);
       if(!matchRegex(iface, tether.getTetherableWifiRegexs())
            && !matchRegex(iface, tether.getTetherableBluetoothRegexs())
            && !iface.equals("BluetoothDun")){
            return false;
        }else{
            int circle = 0;
            boolean set =false;

            while( circle <CHECK_CIRCLE){
                if(matchRegex(iface,tether.getTetherableUsbRegexs()) ){
                    set = mTetherUsbPermissionSet;
                     mTetherUsbPermissionSet = false;
                     Log.i(TAG,"mTetherUsbPermissionSet = "+mTetherUsbPermissionSet);
                }else if (matchRegex(iface,tether.getTetherableWifiRegexs())){
                    set = mMHSPermissionSet;
                    mMHSPermissionSet = false;
                    Log.i(TAG,"mMHSPermissionSet = "+mMHSPermissionSet);
                }else if( matchRegex(iface,tether.getTetherableBluetoothRegexs())){
                     set = mTetherBluetoothPermissionSet;
                     mTetherBluetoothPermissionSet = false;
                     Log.i(TAG,"mTetherBluetoothPermissionSet = "+mTetherBluetoothPermissionSet);
                }else if( iface.equals("BluetoothDun") ){
                     set = mTetherBluetoothDunPermissionSet;
                     mTetherBluetoothDunPermissionSet = false;
                     Log.i(TAG,"longna---mTetherBluetoothDunPermissionSet = "+mTetherBluetoothDunPermissionSet);
                }
                if(set){
                    Log.i(TAG,"result has been set");
                    break;
                }
                try {
                    Thread.sleep(WAIT_CHECK_TIME);
               } catch (InterruptedException e) { }
                circle++;
            }
            Log.i(TAG,"circle = "+circle);

            if(matchRegex(iface,tether.getTetherableUsbRegexs()) )
                return mSPETetherUsbPassed;
            else if(matchRegex(iface,tether.getTetherableWifiRegexs()))
                return mSPEMHSPassed;
            else if ( matchRegex(iface,tether.getTetherableBluetoothRegexs()) ){
                return mSPETetherBluetoothPassed;
            }else if ( iface.equals("BluetoothDun") ){
                Log.i(TAG,"longna----mSPETetherBluetoothDunPassed = "+mSPETetherBluetoothDunPassed);
                return mSPETetherBluetoothDunPassed;
            }else
                return false;
        }
    }

    public boolean getMHSPermission(){
       return mSPEMHSPassed ;
    }

    public void setMHSPermission(boolean flag){
        mSPEMHSPassed =flag;
    }

    public void setMHSPermissionSet(boolean flag){
        mMHSPermissionSet = flag;
    }

    public boolean getTetherUsbPermission(){
        return mSPETetherUsbPassed;
    }

    public void setTetherUsbPermission(boolean flag){
        mSPETetherUsbPassed = flag;
    }

    public void setTetherUsbPermissionSet(boolean flag){
       mTetherUsbPermissionSet = flag;
    }

    public boolean getTetherBluetoothPermission(){
        return mSPETetherBluetoothPassed;
    }

    public void setTetherBluetoothPermission(boolean flag){
       mSPETetherBluetoothPassed = flag;
    }

    public void setTetherBluetoothPermissionSet(boolean flag){
       mTetherBluetoothPermissionSet = flag;
    }

    public boolean getTetherBluetoothDunPermission(){
        return mSPETetherBluetoothDunPassed;
    }

    public void setTetherBluetoothDunPermission(boolean flag){
        mSPETetherBluetoothDunPassed = flag;
    }

    public void setTetherBluetoothDunPermissionSet(boolean flag){
        mTetherBluetoothDunPermissionSet = flag;
    }

    public boolean getPermission(String iface){
         Tethering tether = (Tethering)this;
         if(matchRegex(iface,tether.getTetherableUsbRegexs()) )
             return mSPETetherUsbPassed;
         else if(matchRegex(iface,tether.getTetherableWifiRegexs()))
             return mSPEMHSPassed;
         else if (matchRegex(iface, tether.getTetherableBluetoothRegexs()) ){
             return mSPETetherBluetoothPassed;
         }else if (iface.equals("BluetoothDun")){
             return mSPETetherBluetoothDunPassed;
         }else
             return false;
    }

    public void clearPermission(String iface){
        Tethering tether = (Tethering)this;
        if(matchRegex(iface,tether.getTetherableUsbRegexs()) ){
           mSPETetherUsbPassed =false ;
           mTetherUsbPermissionSet = false;
       }else if(matchRegex(iface,tether.getTetherableWifiRegexs())){
           mSPEMHSPassed = false;
           mMHSPermissionSet = false;
       }else if (matchRegex(iface,tether.getTetherableBluetoothRegexs()) ){
          mSPETetherBluetoothPassed = false;
          mTetherBluetoothPermissionSet = false;
       }else if (iface.equals("BluetoothDun")){
          mSPETetherBluetoothDunPassed = false;
          mTetherBluetoothDunPermissionSet = false;
       }
    }
    public boolean getPermission(){
        return mSPEMHSPassed || mSPETetherUsbPassed || mSPETetherBluetoothPassed || mSPETetherBluetoothDunPassed;
    }
    public boolean matchRegex(String iface, String[] regexes) {
        for (String regex : regexes) {
            if (iface.matches(regex)) {
                return true;
            }
        }
        return false;
    }
    //[FEATURE]-Add-END by chenglin.zhou
    public int untether(String iface) {
        if (DBG) Log.d(TAG, "Untethering " + iface);
        if (SystemProperties.getInt(PROPERTY_ECID,-1) == 50) {
            clearPermission(iface);
        }
        /* MODIFIED-END by zhouchenglin,BUG-5558976*/
        synchronized (mPublicSync) {
            TetherState tetherState = mTetherStates.get(iface);
            if (tetherState == null) {
                Log.e(TAG, "Tried to Untether an unknown iface :" + iface + ", ignoring");
                return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
            }
            if (!tetherState.isCurrentlyServing()) {
                Log.e(TAG, "Tried to untether an inactive iface :" + iface + ", ignoring");
                return ConnectivityManager.TETHER_ERROR_UNAVAIL_IFACE;
            }
            tetherState.stateMachine.sendMessage(
                    TetherInterfaceStateMachine.CMD_TETHER_UNREQUESTED);
            return ConnectivityManager.TETHER_ERROR_NO_ERROR;
        }
    }

    public void untetherAll() {
        stopTethering(ConnectivityManager.TETHERING_WIFI);
        stopTethering(ConnectivityManager.TETHERING_USB);
        stopTethering(ConnectivityManager.TETHERING_BLUETOOTH);
    }

    public int getLastTetherError(String iface) {
        synchronized (mPublicSync) {
            TetherState tetherState = mTetherStates.get(iface);
            if (tetherState == null) {
                Log.e(TAG, "Tried to getLastTetherError on an unknown iface :" + iface +
                        ", ignoring");
                return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
            }
            return tetherState.lastError;
        }
    }

    // TODO: Figure out how to update for local hotspot mode interfaces.
    private void sendTetherStateChangedBroadcast() {
        if (!getConnectivityManager().isTetheringSupported()) return;

        final ArrayList<String> availableList = new ArrayList<>();
        final ArrayList<String> tetherList = new ArrayList<>();
        final ArrayList<String> localOnlyList = new ArrayList<>();
        final ArrayList<String> erroredList = new ArrayList<>();

        boolean wifiTethered = false;
        boolean usbTethered = false;
        boolean bluetoothTethered = false;

        final TetheringConfiguration cfg = mConfig;

        synchronized (mPublicSync) {
            for (int i = 0; i < mTetherStates.size(); i++) {
                TetherState tetherState = mTetherStates.valueAt(i);
                String iface = mTetherStates.keyAt(i);
                if (tetherState.lastError != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    erroredList.add(iface);
                } else if (tetherState.lastState == IControlsTethering.STATE_AVAILABLE) {
                    availableList.add(iface);
                } else if (tetherState.lastState == IControlsTethering.STATE_LOCAL_ONLY) {
                    localOnlyList.add(iface);
                } else if (tetherState.lastState == IControlsTethering.STATE_TETHERED) {
                    if (cfg.isUsb(iface)) {
                        usbTethered = true;
                    } else if (cfg.isWifi(iface)) {
                        wifiTethered = true;
                    } else if (cfg.isBluetooth(iface)) {
                        bluetoothTethered = true;
                    }
                    tetherList.add(iface);
                }
            }
        }
        final Intent bcast = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        bcast.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING |
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        bcast.putStringArrayListExtra(ConnectivityManager.EXTRA_AVAILABLE_TETHER, availableList);
        bcast.putStringArrayListExtra(ConnectivityManager.EXTRA_ACTIVE_LOCAL_ONLY, localOnlyList);
        bcast.putStringArrayListExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, tetherList);
        bcast.putStringArrayListExtra(ConnectivityManager.EXTRA_ERRORED_TETHER, erroredList);
        mContext.sendStickyBroadcastAsUser(bcast, UserHandle.ALL);
        if (DBG) {
            Log.d(TAG, String.format(
                    "sendTetherStateChangedBroadcast %s=[%s] %s=[%s] %s=[%s] %s=[%s]",
                    "avail", TextUtils.join(",", availableList),
                    "local_only", TextUtils.join(",", localOnlyList),
                    "tether", TextUtils.join(",", tetherList),
                    "error", TextUtils.join(",", erroredList)));
        }

        if (usbTethered) {
            if (wifiTethered || bluetoothTethered) {
                showTetheredNotification(SystemMessage.NOTE_TETHER_GENERAL);
            } else {
                showTetheredNotification(SystemMessage.NOTE_TETHER_USB);
            }
        } else if (wifiTethered) {
            if (bluetoothTethered) {
                showTetheredNotification(SystemMessage.NOTE_TETHER_GENERAL);
            } else {
                /* We now have a status bar icon for WifiTethering, so drop the notification */
                clearTetheredNotification();
            }
        } else if (bluetoothTethered) {
            showTetheredNotification(SystemMessage.NOTE_TETHER_BLUETOOTH);
        } else {
            clearTetheredNotification();
        }

        // start:BBSECURE_TETH_TO
        // If MHS is enabled, clear the previous MHS inactivity notification.
        if (wifiTethered) {
            clearMhsInactivityNotification();
        }

        // Update the potential need for a MHS inactivity timer
        evaluateMhsInactiveTimer();
        // end:BBSECURE_TETH_TO
    }

    private void showTetheredNotification(int id) {
        showTetheredNotification(id, true);
    }

    @VisibleForTesting
    protected void showTetheredNotification(int id, boolean tetheringOn) {
        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        int icon = 0;
        switch(id) {
          case SystemMessage.NOTE_TETHER_USB:
            icon = com.android.internal.R.drawable.stat_sys_tether_usb;
            break;
          case SystemMessage.NOTE_TETHER_BLUETOOTH:
            icon = com.android.internal.R.drawable.stat_sys_tether_bluetooth;
            break;
          case SystemMessage.NOTE_TETHER_GENERAL:
          default:
            icon = com.android.internal.R.drawable.stat_sys_tether_general;
            break;
        }

        if (mLastNotificationId != 0) {
            if (mLastNotificationId == icon) {
                return;
            }
            // start:BBSECURE_TETH_TO
            notificationManager.cancelAsUser(TAG_TETHERED_NOTIFICATION, mLastNotificationId,
                    UserHandle.ALL);
            // end:BBSECURE_TETH_TO
            mLastNotificationId = 0;
        }

        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0, intent, 0,
                null, UserHandle.CURRENT);

        Resources r = Resources.getSystem();
        final CharSequence title;
        final CharSequence message;

        if (tetheringOn) {
            title = r.getText(com.android.internal.R.string.tethered_notification_title);
            message = r.getText(com.android.internal.R.string.tethered_notification_message);
        } else {
            title = r.getText(com.android.internal.R.string.disable_tether_notification_title);
            message = r.getText(com.android.internal.R.string.disable_tether_notification_message);
        }

        if (mTetheredNotificationBuilder == null) {
            mTetheredNotificationBuilder =
                    new Notification.Builder(mContext, SystemNotificationChannels.NETWORK_STATUS);
            mTetheredNotificationBuilder.setWhen(0)
                    .setOngoing(true)
                    .setColor(mContext.getColor(
                            com.android.internal.R.color.system_notification_accent_color))
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS);
        }
        mTetheredNotificationBuilder.setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pi);
        mLastNotificationId = id;

        // start:BBSECURE_TETH_TO
        /*
        notificationManager.notifyAsUser(null, mLastNotificationId,
                mTetheredNotificationBuilder.buildInto(new Notification()), UserHandle.ALL);
        */
        notificationManager.notifyAsUser(TAG_TETHERED_NOTIFICATION, mLastNotificationId,
                mTetheredNotificationBuilder.buildInto(new Notification()), UserHandle.ALL);
        // end:BBSECURE_TETH_TO
    }

    @VisibleForTesting
    protected void clearTetheredNotification() {
        NotificationManager notificationManager =
            (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && mLastNotificationId != 0) {
            // start:BBSECURE_TETH_TO
            /*
            notificationManager.cancelAsUser(null, mLastNotificationId,
                    UserHandle.ALL);
            */
            notificationManager.cancelAsUser(TAG_TETHERED_NOTIFICATION, mLastNotificationId,
                    UserHandle.ALL);
            // end:BBSECURE_TETH_TO
            mLastNotificationId = 0;
        }
    }

    // start:BBSECURE_TETH_TO
    private void mhsInactivityTimeout() {
        synchronized (mPublicSync) {
            if (mMhsInactivityPendingIntent != null) {
                if (DBG) Log.d(TAG, "mhsInactivityTimerout - request MHS tethering shutdown at this point");
                stopTethering(ConnectivityManager.TETHERING_WIFI);
                WifiManager wifiManager =
                        (WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);

                if (wifiManager != null) {
                    // disable MHS
                    wifiManager.setWifiApEnabled(null, false);

                    int wifiSavedState = 0;
                    final ContentResolver cr = mContext.getContentResolver();

                    try {
                        wifiSavedState = Settings.Global.getInt(cr, Settings.Global.WIFI_SAVED_STATE);
                    } catch (Settings.SettingNotFoundException e) {
                        if (DBG) Log.d(TAG, "mhsInactivityTimerout - cannot read the saved Wi-Fi state, assuming it is disabled when starting MHS");
                    }

                    // If needed, restore Wi-Fi on tether disable
                    if (wifiSavedState == 1) {
                        wifiManager.setWifiEnabled(true);
                        Settings.Global.putInt(cr, Settings.Global.WIFI_SAVED_STATE, 0);
                    }
                    clearTetheredNotification();
                    showMhsInactivityNotification();
                }
                mMhsInactivityPendingIntent = null;
            }
        }
    }

    private void ensureMhsInactiveTimerRunning() {
        final ContentResolver cr = mContext.getContentResolver();
        try {
            mMhsInactivityTimeout = Settings.Global.getInt(cr, Settings.Global.WIFI_HOTSPOT_INACTIVITY_TIMEOUT_MIN);
        } catch (Settings.SettingNotFoundException se) {
            // Use the default MHS inactivity timeout value from the resource. Then we can configure
            // this value on a per carrier basis.
            Resources r = Resources.getSystem();
            try {
                mMhsInactivityTimeout = r.getInteger(com.android.internal.R.integer.mhs_inactivity_timeout_mins);
                if (DBG) Log.d(TAG, "Couldn't find wifi_mhs_inactivity_timeout_in_mins, use the value from resources: " + mMhsInactivityTimeout + " mins");
            } catch (Resources.NotFoundException re) {
                mMhsInactivityTimeout = 10;
                if (DBG) Log.d(TAG, "Couldn't get the inactivity timeout value from settings or resources, defaulting to " + mMhsInactivityTimeout + " mins");
            }
        }

        if (mMhsInactivityTimeout <= 0) {
            // The inactivity timer is disabled by the user.
            stopMhsInactiveTimer();
            return;
        }

        synchronized (mPublicSync) {
            AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            if (mMhsInactivityPendingIntent != null) {
                alarmManager.cancel(mMhsInactivityPendingIntent);
                mMhsInactivityPendingIntent = null;
            }

            if (DBG) Log.d(TAG, "ensureMHSInactiveTimerRunning - starting MHS inactivity timer for " +
                    mMhsInactivityTimeout + " mins");

            Intent i = new Intent(ACTION_MHS_INACTIVITY_ALARM);
            mMhsInactivityPendingIntent = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + mMhsInactivityTimeout*60*1000, mMhsInactivityPendingIntent);
        }
    }

    private void stopMhsInactiveTimer() {
        synchronized (mPublicSync) {
            if (mMhsInactivityPendingIntent != null) {
                if (DBG) Log.d(TAG, "stopMHSInactiveTimer - removing MHS inactivity timer");
                AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(mMhsInactivityPendingIntent);
                mMhsInactivityPendingIntent = null;
            }
        }
    }

    private void evaluateMhsInactiveTimer() {
        synchronized (mPublicSync) {
            // If on external power, don't care about inactive MHS
            if (mDeviceHasExternalPower) {
                if (DBG) Log.d(TAG, "evaluateMHSInactiveTimer: have external power, cancel any MHS inactive timer");
                stopMhsInactiveTimer();
                return;
            }

            // Only care about inactive MHS if only MHS is tethered
            final TetheringConfiguration cfg = mConfig;
            boolean wifiTethered = false;
            String[] tetheredIfs = getTetheredIfaces();
            if (tetheredIfs != null) {
                for (String iface : tetheredIfs) {
                    if (cfg.isWifi(iface)) {
                        wifiTethered = true;
                        break;
                    }
                }
            }

            if (DBG) Log.d(TAG, "evaluateMHSInactiveTimer: MHS=" + wifiTethered);
            if (!wifiTethered) {
                Log.d(TAG, "evaluateMHSInactiveTimer: stop timer -- MHS=" + wifiTethered);
                stopMhsInactiveTimer();
                return;
            }

            // See if any clients connected to MHS
            final WifiManager wifiManager =
                    (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager.getConnectedStations().isEmpty()) {
                // At this point want to have the inactivity timer running
                if (DBG) Log.d(TAG, "MHS tethering, no connected clients");
                ensureMhsInactiveTimerRunning();
            } else {
                // Have MHS clients, cancel any inactivity timers
                if (DBG) Log.d(TAG, "MHS tethering, but have connected clients");
                stopMhsInactiveTimer();
            }
        }
    }

    private void showMhsInactivityNotification() {
        // Generate a notification when MHS session terminates due to inactivity timeout
        NotificationManager notificationManager =
                (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) {
            return;
        }

        if (mMhsInactivityNotification != null) {
            // remove the existing MHS inactivity timeout notification
            notificationManager.cancelAsUser(TAG_MHS_INACTIVITY_NOTIFICATION, MHS_TIME_OUT_ICON,
                    UserHandle.ALL);
            mMhsInactivityNotification = null;
        }

        // Set up the Intent that will be issued when the user clicks the notification
        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.setAction("com.android.settings.WIFI_AP_SETTINGS");

        PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0, intent, 0,
                null, UserHandle.CURRENT);

        Resources r = Resources.getSystem();
        CharSequence title = r.getText(com.android.internal.R.string.mhs_inactivity_notification_title);
        CharSequence message = r.getText(com.android.internal.R.string.mhs_inactivity_notification_message);

        if (mMhsInactivityNotification == null) {
            mMhsInactivityNotification =
                    new Notification.Builder(mContext, SystemNotificationChannels.NETWORK_STATUS);
            mMhsInactivityNotification.setWhen(0)
                    //.setOngoing(true) // MODIFIED by youcheng.gao, 2018-07-10,BUG-6565093
                    .setColor(mContext.getColor(
                            com.android.internal.R.color.system_notification_accent_color))
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setAutoCancel(true);
        }
        mMhsInactivityNotification.setSmallIcon(MHS_TIME_OUT_ICON)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pi);
        // Display the notification
        notificationManager.notifyAsUser(TAG_MHS_INACTIVITY_NOTIFICATION, MHS_TIME_OUT_ICON,
                mMhsInactivityNotification.build(), UserHandle.ALL);
    }

    private void clearMhsInactivityNotification() {
        // Remove the current MHS inactivity notification
        NotificationManager notificationManager =
            (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && mMhsInactivityNotification != null) {
            notificationManager.cancelAsUser(TAG_MHS_INACTIVITY_NOTIFICATION, MHS_TIME_OUT_ICON,
                    UserHandle.ALL);
            mMhsInactivityNotification = null;
        }
    }
    // end:BBSECURE_TETH_TO

    private class StateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            final String action = intent.getAction(); // MODIFIED by zhouchenglin, 2017-11-03,BUG-5558976
            if (action == null) return;

            if (action.equals(UsbManager.ACTION_USB_STATE)) {
                handleUsbAction(intent);
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                handleConnectivityAction(intent);
            } else if (action.equals(WifiManager.WIFI_AP_STATE_CHANGED_ACTION)) {
                handleWifiApAction(intent);
                // start:BBSECURE_TETH_TO
                evaluateMhsInactiveTimer();
                // end:BBSECURE_TETH_TO
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                updateConfiguration();
            /* MODIFIED-BEGIN by zhouchenglin, 2017-12-08,BUG-5719037*/
            }else if(action.equals(ACTION_CONNECTIVITY_APN_FAIL)){
                Log.d(TAG, "action.connectivity.apn.failed");
                setWifiTethering(false);
                sendTetherStateChangedBroadcast();
                /* MODIFIED-END by zhouchenglin,BUG-5719037*/
            }
            //[FEATURE]-Add-BEGIN by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
            else if (action.equals(ACTION_SERVICE_ENTITLEMENT_RESULT)) {
                String iface = intent.getStringExtra(CHECK_IFACE);
                /* MODIFIED-BEGIN by sunyandong, 2017-03-10,BUG-4280174*/
                Log.i(TAG,"iface = " + iface +  "action: "+action);
                if(iface !=null){
                /* MODIFIED-END by sunyandong,BUG-4280174*/
                    if (matchRegex(iface, getTetherableUsbRegexs()) ){
                        setTetherUsbPermission(intent.getBooleanExtra(CHECK_RESULT, false));
                        setTetherUsbPermissionSet(true);
                        Log.i(TAG,"usb, check result = "+getTetherUsbPermission());
                        if(getTetherUsbPermission())
                            /* MODIFIED-BEGIN by yinbimin, 2017-05-03,BUG-4620782*/
                            //tether( iface);
                            tether_internel(iface,IControlsTethering.STATE_TETHERED);
                            /* MODIFIED-END by yinbimin,BUG-4620782*/
                        else{
                            mTetherStates.remove(iface);
                            setUsbTethering(false);
                            sendTetherStateChangedBroadcast();
                        }
                    }else if (matchRegex(iface,getTetherableWifiRegexs() )){
                        setMHSPermission(intent.getBooleanExtra(CHECK_RESULT, false));
                        setMHSPermissionSet(true);
                        Log.i(TAG,"mhs, check result = "+getMHSPermission());
                        if(getMHSPermission())
                            /* MODIFIED-BEGIN by yinbimin, 2017-05-03,BUG-4620782*/
                            //tether( iface);
                            tether_internel(iface,IControlsTethering.STATE_TETHERED);
                            /* MODIFIED-END by yinbimin,BUG-4620782*/
                        else{
                            mTetherStates.remove(iface);
                            setWifiTethering(false);
                            sendTetherStateChangedBroadcast();
                        }
                    }else if ( matchRegex(iface,getTetherableBluetoothRegexs()) ){
                        setTetherBluetoothPermission(intent.getBooleanExtra(CHECK_RESULT, false));
                        setTetherBluetoothPermissionSet(true);
                        Log.i(TAG,"bluetooth, check result = "+getTetherBluetoothPermission());
                    }else if ( iface.equals("BluetoothDun") ){
                        setTetherBluetoothDunPermission(intent.getBooleanExtra(CHECK_RESULT, false));
                        setTetherBluetoothDunPermissionSet(true);
                        Log.i(TAG,"bluetooth, check result = "+getTetherBluetoothDunPermission());
                    }
                }
            }
            //[FEATURE]-Add-END by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
            // start:BBSECURE_TETH_TO
            else if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
                mDeviceHasExternalPower = true;
                if (DBG) Log.d(TAG, "ACTION_POWER_CONNECTED");
                evaluateMhsInactiveTimer();
            } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                mDeviceHasExternalPower = false;
                if (DBG) Log.d(TAG, "ACTION_POWER_DISCONNECTED");
                evaluateMhsInactiveTimer();
            } else if (action.equals(ACTION_MHS_INACTIVITY_ALARM)) {
                if (DBG) Log.d(TAG, ACTION_MHS_INACTIVITY_ALARM + " received");
                mhsInactivityTimeout();
            } else if (action.equals(ConnectivityManager.TETHER_CONNECT_STATE_CHANGED)) {
                evaluateMhsInactiveTimer();
            }
            // end:BBSECURE_TETH_TO
        }

        private void handleConnectivityAction(Intent intent) {
            final NetworkInfo networkInfo =(NetworkInfo) intent.getParcelableExtra(
                    ConnectivityManager.EXTRA_NETWORK_INFO);
            if (networkInfo == null ||
                    networkInfo.getDetailedState() == NetworkInfo.DetailedState.FAILED) {
                return;
            }

            if (VDBG) Log.d(TAG, "Tethering got CONNECTIVITY_ACTION: " + networkInfo.toString());
            mTetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
        }

        private void handleUsbAction(Intent intent) {
            final boolean usbConnected = intent.getBooleanExtra(USB_CONNECTED, false);
            final boolean usbConfigured = intent.getBooleanExtra(USB_CONFIGURED, false);
/* MODIFIED-BEGIN by na.long, 2017-11-09,BUG-5586498*/
// start:BBRY_ANDROID
/*
            final boolean rndisEnabled = intent.getBooleanExtra(USB_FUNCTION_RNDIS, false);
*/
            final boolean rndisEnabled = intent.getBooleanExtra(UsbManager.USB_FUNCTION_NCM, false);
// end:BBRY_ANDROID
/* MODIFIED-END by na.long,BUG-5586498*/

            mLog.log(String.format("USB bcast connected:%s configured:%s rndis:%s",
                    usbConnected, usbConfigured, rndisEnabled));

            // There are three types of ACTION_USB_STATE:
            //
            //     - DISCONNECTED (USB_CONNECTED and USB_CONFIGURED are 0)
            //       Meaning: USB connection has ended either because of
            //       software reset or hard unplug.
            //
            //     - CONNECTED (USB_CONNECTED is 1, USB_CONFIGURED is 0)
            //       Meaning: the first stage of USB protocol handshake has
            //       occurred but it is not complete.
            //
            //     - CONFIGURED (USB_CONNECTED and USB_CONFIGURED are 1)
            //       Meaning: the USB handshake is completely done and all the
            //       functions are ready to use.
            //
            // For more explanation, see b/62552150 .
            synchronized (Tethering.this.mPublicSync) {
                // Always record the state of RNDIS.
                // TODO: consider:
                //     final boolean disconnected = !usbConnected;
                //     if (disconnected) {
                //         mRndisEnabled = false;
                //         mUsbTetherRequested = false;
                //         return;
                //     }
                //     final boolean configured = usbConnected && usbConfigured;
                //     mRndisEnabled = configured ? rndisEnabled : false;
                //     if (!configured) return;
                mRndisEnabled = rndisEnabled;

                if (usbConnected && !usbConfigured) {
                    // Nothing to do here (only CONNECTED, not yet CONFIGURED).
                    return;
                }

                // start tethering if we have a request pending
                if (usbConfigured && mRndisEnabled && mUsbTetherRequested) {
                    tetherMatchingInterfaces(
                            IControlsTethering.STATE_TETHERED,
                            ConnectivityManager.TETHERING_USB);
                }

                // TODO: Figure out how to remove the need for this variable.
                mUsbTetherRequested = false;
            }
        }

        private void handleWifiApAction(Intent intent) {
            final int curState = intent.getIntExtra(EXTRA_WIFI_AP_STATE, WIFI_AP_STATE_DISABLED);
            final String ifname = intent.getStringExtra(EXTRA_WIFI_AP_INTERFACE_NAME);
            final int ipmode = intent.getIntExtra(EXTRA_WIFI_AP_MODE, IFACE_IP_MODE_UNSPECIFIED);

            synchronized (Tethering.this.mPublicSync) {
                switch (curState) {
                    case WifiManager.WIFI_AP_STATE_ENABLING:
                        // We can see this state on the way to both enabled and failure states.
                        break;
                    case WifiManager.WIFI_AP_STATE_ENABLED:
                        enableWifiIpServingLocked(ifname, ipmode);
                        break;
                    case WifiManager.WIFI_AP_STATE_DISABLED:
                    case WifiManager.WIFI_AP_STATE_DISABLING:
                    case WifiManager.WIFI_AP_STATE_FAILED:
                    default:
                        disableWifiIpServingLocked(ifname, curState);
                        break;
                }
            }
        }
    }

    @VisibleForTesting
    protected static class TetheringUserRestrictionListener implements UserRestrictionsListener {
        private final Tethering mWrapper;

        public TetheringUserRestrictionListener(Tethering wrapper) {
            mWrapper = wrapper;
        }

        public void onUserRestrictionsChanged(int userId,
                                              Bundle newRestrictions,
                                              Bundle prevRestrictions) {
            final boolean newlyDisallowed =
                    newRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_TETHERING);
            final boolean previouslyDisallowed =
                    prevRestrictions.getBoolean(UserManager.DISALLOW_CONFIG_TETHERING);
            final boolean tetheringDisallowedChanged = (newlyDisallowed != previouslyDisallowed);

            if (!tetheringDisallowedChanged) {
                return;
            }

            mWrapper.clearTetheredNotification();
            final boolean isTetheringActiveOnDevice = (mWrapper.getTetheredIfaces().length != 0);

            if (newlyDisallowed && isTetheringActiveOnDevice) {
                mWrapper.showTetheredNotification(
                        com.android.internal.R.drawable.stat_sys_tether_general, false);
                mWrapper.untetherAll();
            }
        }
    }

    private void disableWifiIpServingLocked(String ifname, int apState) {
        mLog.log("Canceling WiFi tethering request - AP_STATE=" + apState);

        // Regardless of whether we requested this transition, the AP has gone
        // down.  Don't try to tether again unless we're requested to do so.
        // TODO: Remove this altogether, once Wi-Fi reliably gives us an
        // interface name with every broadcast.
        mWifiTetherRequested = false;

        if (!TextUtils.isEmpty(ifname)) {
            final TetherState ts = mTetherStates.get(ifname);
            if (ts != null) {
                ts.stateMachine.unwanted();
                return;
            }
        }

        for (int i = 0; i < mTetherStates.size(); i++) {
            TetherInterfaceStateMachine tism = mTetherStates.valueAt(i).stateMachine;
            if (tism.interfaceType() == ConnectivityManager.TETHERING_WIFI) {
                tism.unwanted();
                return;
            }
        }

        mLog.log("Error disabling Wi-Fi IP serving; " +
                (TextUtils.isEmpty(ifname) ? "no interface name specified"
                                           : "specified interface: " + ifname));
    }

    private void enableWifiIpServingLocked(String ifname, int wifiIpMode) {
        // Map wifiIpMode values to IControlsTethering serving states, inferring
        // from mWifiTetherRequested as a final "best guess".
        final int ipServingMode;
        switch (wifiIpMode) {
            case IFACE_IP_MODE_TETHERED:
                ipServingMode = IControlsTethering.STATE_TETHERED;
                break;
            case IFACE_IP_MODE_LOCAL_ONLY:
                ipServingMode = IControlsTethering.STATE_LOCAL_ONLY;
                break;
            default:
                mLog.e("Cannot enable IP serving in unknown WiFi mode: " + wifiIpMode);
                return;
        }

        if (!TextUtils.isEmpty(ifname)) {
            maybeTrackNewInterfaceLocked(ifname, ConnectivityManager.TETHERING_WIFI);
            changeInterfaceState(ifname, ipServingMode);
        } else {
            mLog.e(String.format(
                   "Cannot enable IP serving in mode %s on missing interface name",
                   ipServingMode));
        }
    }

    // TODO: Consider renaming to something more accurate in its description.
    // This method:
    //     - allows requesting either tethering or local hotspot serving states
    //     - handles both enabling and disabling serving states
    //     - only tethers the first matching interface in listInterfaces()
    //       order of a given type
    private void tetherMatchingInterfaces(int requestedState, int interfaceType) {
        if (VDBG) {
            Log.d(TAG, "tetherMatchingInterfaces(" + requestedState + ", " + interfaceType + ")");
        }

        String[] ifaces = null;
        try {
            ifaces = mNMService.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "Error listing Interfaces", e);
            return;
        }
        String chosenIface = null;
        if (ifaces != null) {
            for (String iface : ifaces) {
                if (ifaceNameToType(iface) == interfaceType) {
                    chosenIface = iface;
                    break;
                }
            }
        }
        if (chosenIface == null) {
            Log.e(TAG, "could not find iface of type " + interfaceType);
            return;
        }

        changeInterfaceState(chosenIface, requestedState);
    }

    private void changeInterfaceState(String ifname, int requestedState) {
        final int result;
        switch (requestedState) {
            case IControlsTethering.STATE_UNAVAILABLE:
            case IControlsTethering.STATE_AVAILABLE:
                result = untether(ifname);
                break;
            case IControlsTethering.STATE_TETHERED:
            case IControlsTethering.STATE_LOCAL_ONLY:
                result = tether(ifname, requestedState);
                break;
            default:
                Log.wtf(TAG, "Unknown interface state: " + requestedState);
                return;
        }
        if (result != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
            Log.e(TAG, "unable start or stop tethering on iface " + ifname);
            return;
        }
    }

    public TetheringConfiguration getTetheringConfiguration() {
        return mConfig;
    }

    public boolean hasTetherableConfiguration() {
        final TetheringConfiguration cfg = mConfig;
        final boolean hasDownstreamConfiguration =
                (cfg.tetherableUsbRegexs.length != 0) ||
                (cfg.tetherableWifiRegexs.length != 0) ||
                (cfg.tetherableBluetoothRegexs.length != 0);
        final boolean hasUpstreamConfiguration = !cfg.preferredUpstreamIfaceTypes.isEmpty();

        return hasDownstreamConfiguration && hasUpstreamConfiguration;
    }

    // TODO - update callers to use getTetheringConfiguration(),
    // which has only final members.
    public String[] getTetherableUsbRegexs() {
        return copy(mConfig.tetherableUsbRegexs);
    }

    public String[] getTetherableWifiRegexs() {
        return copy(mConfig.tetherableWifiRegexs);
    }

    public String[] getTetherableBluetoothRegexs() {
        return copy(mConfig.tetherableBluetoothRegexs);
    }

    public int setUsbTethering(boolean enable) {
        /* MODIFIED-BEGIN by zhouchenglin, 2017-12-08,BUG-5719037*/
        if(isAtt)
            mUsbTetherFlag = enable;
            /* MODIFIED-END by zhouchenglin,BUG-5719037*/
//[BBSOLUTION]-Add-BEGIN by TCTNB, 09/30/2017, SOLUTION-5365403
//Sprint solution
        /* MODIFIED-BEGIN by shu.wang-nb, 2017-03-13,BUG-4353140*/
        //ADD-BEGIN by shuchun.lin.sz@tcl.com 2016.03.09 for OMADM 1733399
        if (SystemProperties.isSprintEcid()) {
            // Add for DeviceManagement for [FR-939452][GTR-SAE-046] by yuandong.zheng@jrdcom.com AT 2015-03-03 BEGIN
            DevicePolicyManager mDeviceManager = (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
            boolean isDisableUsbTether = mDeviceManager.getPolicyRestricted(null, DevicePolicyManager.POLICY_USB_DATA);
            if (isDisableUsbTether) {
                enable = false;
                if (VDBG) Log.d(TAG, "setUsbTethering(" + enable + ") for device is restricted");
            }
            // Add for DeviceManagement for [FR-939452][GTR-SAE-046] by yuandong.zheng@jrdcom.com AT 2015-03-03 END
            //ADD-END by shuchun.lin.sz@tcl.com 2016.03.09 for OMADM 1733399
        }
        /* MODIFIED-END by shu.wang-nb,BUG-4353140*/
//[BBSOLUTION]-Add-END by TCTNB)

        if (VDBG) Log.d(TAG, "setUsbTethering(" + enable + ")");
        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        synchronized (mPublicSync) {
            if (enable) {
                if (mRndisEnabled) {
                    final long ident = Binder.clearCallingIdentity();
                    try {
                        tetherMatchingInterfaces(IControlsTethering.STATE_TETHERED,
                                ConnectivityManager.TETHERING_USB);
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                } else {
                    mUsbTetherRequested = true;
/* MODIFIED-BEGIN by na.long, 2017-11-09,BUG-5586498*/
// start:BBRY_ANDROID
/*
                    usbManager.setCurrentFunction(UsbManager.USB_FUNCTION_RNDIS, false);
*/
                    usbManager.setCurrentFunction(UsbManager.USB_FUNCTION_NCM, false);
// end:BBRY_ANDROID
/* MODIFIED-END by na.long,BUG-5586498*/
                }
            } else {
                final long ident = Binder.clearCallingIdentity();
                try {
                    tetherMatchingInterfaces(IControlsTethering.STATE_AVAILABLE,
                            ConnectivityManager.TETHERING_USB);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
                if (mRndisEnabled) {
                    usbManager.setCurrentFunction(null, false);
                }
                mUsbTetherRequested = false;
            }
        }
        return ConnectivityManager.TETHER_ERROR_NO_ERROR;
    }

    // TODO review API - figure out how to delete these entirely.
    public String[] getTetheredIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            for (int i = 0; i < mTetherStates.size(); i++) {
                TetherState tetherState = mTetherStates.valueAt(i);
                if (tetherState.lastState == IControlsTethering.STATE_TETHERED) {
                    list.add(mTetherStates.keyAt(i));
                }
            }
        }
        return list.toArray(new String[list.size()]);
    }

    public String[] getTetherableIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            for (int i = 0; i < mTetherStates.size(); i++) {
                TetherState tetherState = mTetherStates.valueAt(i);
                if (tetherState.lastState == IControlsTethering.STATE_AVAILABLE) {
                    list.add(mTetherStates.keyAt(i));
                }
            }
        }
        return list.toArray(new String[list.size()]);
    }

    public String[] getTetheredDhcpRanges() {
        return mConfig.dhcpRanges;
    }

    public String[] getErroredIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            for (int i = 0; i < mTetherStates.size(); i++) {
                TetherState tetherState = mTetherStates.valueAt(i);
                if (tetherState.lastError != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    list.add(mTetherStates.keyAt(i));
                }
            }
        }
        return list.toArray(new String[list.size()]);
    }

    private void logMessage(State state, int what) {
        mLog.log(state.getName() + " got " + sMagicDecoderRing.get(what, Integer.toString(what)));
    }

    private boolean upstreamWanted() {
        if (!mForwardedDownstreams.isEmpty()) return true;

        synchronized (mPublicSync) {
            return mUsbTetherRequested || mWifiTetherRequested;
        }
    }

    // Needed because the canonical source of upstream truth is just the
    // upstream interface name, |mCurrentUpstreamIface|.  This is ripe for
    // future simplification, once the upstream Network is canonical.
    private boolean pertainsToCurrentUpstream(NetworkState ns) {
        if (ns != null && ns.linkProperties != null && mCurrentUpstreamIface != null) {
            for (String ifname : ns.linkProperties.getAllInterfaceNames()) {
                if (mCurrentUpstreamIface.equals(ifname)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reevaluateSimCardProvisioning() {
        if (!hasMobileHotspotProvisionApp()) return;

        ArrayList<Integer> tethered = new ArrayList<>();
        synchronized (mPublicSync) {
            for (int i = 0; i < mTetherStates.size(); i++) {
                TetherState tetherState = mTetherStates.valueAt(i);
                if (tetherState.lastState != IControlsTethering.STATE_TETHERED) {
                    continue;  // Skip interfaces that aren't tethered.
                }
                String iface = mTetherStates.keyAt(i);
                int interfaceType = ifaceNameToType(iface);
                if (interfaceType != ConnectivityManager.TETHERING_INVALID) {
                    tethered.add(interfaceType);
                }
            }
        }

        for (int tetherType : tethered) {
            startProvisionIntent(tetherType);
        }
    }

    class TetherMasterSM extends StateMachine {
        private static final int BASE_MASTER                    = Protocol.BASE_TETHERING;
        // an interface SM has requested Tethering/Local Hotspot
        static final int EVENT_IFACE_SERVING_STATE_ACTIVE       = BASE_MASTER + 1;
        // an interface SM has unrequested Tethering/Local Hotspot
        static final int EVENT_IFACE_SERVING_STATE_INACTIVE     = BASE_MASTER + 2;
        // upstream connection change - do the right thing
        static final int CMD_UPSTREAM_CHANGED                   = BASE_MASTER + 3;
        // we don't have a valid upstream conn, check again after a delay
        static final int CMD_RETRY_UPSTREAM                     = BASE_MASTER + 4;
        // Events from NetworkCallbacks that we process on the master state
        // machine thread on behalf of the UpstreamNetworkMonitor.
        static final int EVENT_UPSTREAM_CALLBACK                = BASE_MASTER + 5;
        // we treated the error and want now to clear it
        static final int CMD_CLEAR_ERROR                        = BASE_MASTER + 6;
        static final int EVENT_IFACE_UPDATE_LINKPROPERTIES      = BASE_MASTER + 7;

        private final State mInitialState;
        private final State mTetherModeAliveState;

        private final State mSetIpForwardingEnabledErrorState;
        private final State mSetIpForwardingDisabledErrorState;
        private final State mStartTetheringErrorState;
        private final State mStopTetheringErrorState;
        private final State mSetDnsForwardersErrorState;

        // This list is a little subtle.  It contains all the interfaces that currently are
        // requesting tethering, regardless of whether these interfaces are still members of
        // mTetherStates.  This allows us to maintain the following predicates:
        //
        // 1) mTetherStates contains the set of all currently existing, tetherable, link state up
        //    interfaces.
        // 2) mNotifyList contains all state machines that may have outstanding tethering state
        //    that needs to be torn down.
        //
        // Because we excise interfaces immediately from mTetherStates, we must maintain mNotifyList
        // so that the garbage collector does not clean up the state machine before it has a chance
        // to tear itself down.
        private final ArrayList<TetherInterfaceStateMachine> mNotifyList;
        private final IPv6TetheringCoordinator mIPv6TetheringCoordinator;
        private final OffloadWrapper mOffload;

        private static final int UPSTREAM_SETTLE_TIME_MS     = 10000;

        TetherMasterSM(String name, Looper looper) {
            super(name, looper);

            mInitialState = new InitialState();
            mTetherModeAliveState = new TetherModeAliveState();
            mSetIpForwardingEnabledErrorState = new SetIpForwardingEnabledErrorState();
            mSetIpForwardingDisabledErrorState = new SetIpForwardingDisabledErrorState();
            mStartTetheringErrorState = new StartTetheringErrorState();
            mStopTetheringErrorState = new StopTetheringErrorState();
            mSetDnsForwardersErrorState = new SetDnsForwardersErrorState();

            addState(mInitialState);
            addState(mTetherModeAliveState);
            addState(mSetIpForwardingEnabledErrorState);
            addState(mSetIpForwardingDisabledErrorState);
            addState(mStartTetheringErrorState);
            addState(mStopTetheringErrorState);
            addState(mSetDnsForwardersErrorState);

            mNotifyList = new ArrayList<>();
            mIPv6TetheringCoordinator = new IPv6TetheringCoordinator
                           (mNotifyList, mLog, v6OnlyTetherEnabled);
            mOffload = new OffloadWrapper();

            setInitialState(mInitialState);
        }

        class InitialState extends State {
            @Override
            public boolean processMessage(Message message) {
                logMessage(this, message.what);
                switch (message.what) {
                    case EVENT_IFACE_SERVING_STATE_ACTIVE:
                        TetherInterfaceStateMachine who = (TetherInterfaceStateMachine) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode requested by " + who);
                        handleInterfaceServingStateActive(message.arg1, who);
                        transitionTo(mTetherModeAliveState);
                        break;
                    case EVENT_IFACE_SERVING_STATE_INACTIVE:
                        who = (TetherInterfaceStateMachine) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode unrequested by " + who);
                        handleInterfaceServingStateInactive(who);
                        break;
                    case EVENT_IFACE_UPDATE_LINKPROPERTIES:
                        // Silently ignore these for now.
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }

        protected boolean turnOnMasterTetherSettings() {
            final TetheringConfiguration cfg = mConfig;
            try {
                mNMService.setIpForwardingEnabled(true);
            } catch (Exception e) {
                mLog.e(e);
                transitionTo(mSetIpForwardingEnabledErrorState);
                return false;
            }
            // TODO: Randomize DHCPv4 ranges, especially in hotspot mode.
            try {
                // TODO: Find a more accurate method name (startDHCPv4()?).
                mNMService.startTethering(cfg.dhcpRanges);
            } catch (Exception e) {
                try {
                    mNMService.stopTethering();
                    mNMService.startTethering(cfg.dhcpRanges);
                } catch (Exception ee) {
                    mLog.e(ee);
                    transitionTo(mStartTetheringErrorState);
                    return false;
                }
            }
            mLog.log("SET master tether settings: ON");
            return true;
        }

        protected boolean turnOffMasterTetherSettings() {
            try {
                mNMService.stopTethering();
            } catch (Exception e) {
                mLog.e(e);
                transitionTo(mStopTetheringErrorState);
                return false;
            }
            try {
                mNMService.setIpForwardingEnabled(false);
            } catch (Exception e) {
                mLog.e(e);
                transitionTo(mSetIpForwardingDisabledErrorState);
                return false;
            }
            transitionTo(mInitialState);
            mLog.log("SET master tether settings: OFF");
            return true;
        }

        protected void chooseUpstreamType(boolean tryCell) {
            // We rebuild configuration on ACTION_CONFIGURATION_CHANGED, but we
            // do not currently know how to watch for changes in DUN settings.
            maybeUpdateConfiguration();

            final NetworkState ns = mUpstreamNetworkMonitor.selectPreferredUpstreamType(
                    mConfig.preferredUpstreamIfaceTypes);
            if (ns == null) {
                if (tryCell) {
                    mUpstreamNetworkMonitor.registerMobileNetworkRequest();
                    // We think mobile should be coming up; don't set a retry.
                } else {
                    sendMessageDelayed(CMD_RETRY_UPSTREAM, UPSTREAM_SETTLE_TIME_MS);
                }
            }
            setUpstreamNetwork(ns);
        }

        protected void setUpstreamNetwork(NetworkState ns) {
            String iface = null;
            if (ns != null && ns.linkProperties != null) {
                // Find the interface with the default IPv4 route. It may be the
                // interface described by linkProperties, or one of the interfaces
                // stacked on top of it.
                mLog.i("Finding IPv4 upstream interface on: " + ns.linkProperties);
                RouteInfo ipv4Default = RouteInfo.selectBestRoute(
                    ns.linkProperties.getAllRoutes(), Inet4Address.ANY);
                if (ipv4Default != null) {
                    iface = ipv4Default.getInterface();

                    Log.i(TAG, "Found V4 interface " + ipv4Default.getInterface());

                } else {
                    RouteInfo ipv6Default = RouteInfo.selectBestRoute(
                        ns.linkProperties.getAllRoutes(), Inet6Address.ANY);
                    if(v6OnlyTetherEnabled) {
                        if (ipv6Default != null) {
                            iface = ipv6Default.getInterface();

                            Log.i(TAG, "Found V6 interface " + ipv6Default.getInterface());
                        } else {
                            Log.i(TAG, "No IPv6 upstream interface");
                        }
                    } else {

                        mLog.i("No upstream interface, giving up.");
                    }
                }
            }

            if (iface != null) {
                setDnsForwarders(ns.network, ns.linkProperties);
            }
            notifyDownstreamsOfNewUpstreamIface(iface);
            if (ns != null && pertainsToCurrentUpstream(ns)) {
                // If we already have NetworkState for this network examine
                // it immediately, because there likely will be no second
                // EVENT_ON_AVAILABLE (it was already received).
                handleNewUpstreamNetworkState(ns);
            } else if (mCurrentUpstreamIface == null) {
                // There are no available upstream networks, or none that
                // have an IPv4 default route (current metric for success).
                handleNewUpstreamNetworkState(null);
            }
        }

        protected void setDnsForwarders(final Network network, final LinkProperties lp) {
            // TODO: Set v4 and/or v6 DNS per available connectivity.
            String[] dnsServers = mConfig.defaultIPv4DNS;
            final Collection<InetAddress> dnses = lp.getDnsServers();
            // TODO: Properly support the absence of DNS servers.
            if (dnses != null && !dnses.isEmpty()) {
                // TODO: remove this invocation of NetworkUtils.makeStrings().
                dnsServers = NetworkUtils.makeStrings(dnses);
            }
            try {
                mNMService.setDnsForwarders(network, dnsServers);
                mLog.log(String.format(
                        "SET DNS forwarders: network=%s dnsServers=%s",
                        network, Arrays.toString(dnsServers)));
            } catch (Exception e) {
                // TODO: Investigate how this can fail and what exactly
                // happens if/when such failures occur.
                mLog.e("setting DNS forwarders failed, " + e);
                transitionTo(mSetDnsForwardersErrorState);
            }
        }

        protected void notifyDownstreamsOfNewUpstreamIface(String ifaceName) {
            mLog.log("Notifying downstreams of upstream=" + ifaceName);
            mCurrentUpstreamIface = ifaceName;
            for (TetherInterfaceStateMachine sm : mNotifyList) {
                sm.sendMessage(TetherInterfaceStateMachine.CMD_TETHER_CONNECTION_CHANGED,
                        ifaceName);
            }
        }

        protected void handleNewUpstreamNetworkState(NetworkState ns) {
            mIPv6TetheringCoordinator.updateUpstreamNetworkState(ns);
            mOffload.updateUpstreamNetworkState(ns);
        }

        private void handleInterfaceServingStateActive(int mode, TetherInterfaceStateMachine who) {
            if (mNotifyList.indexOf(who) < 0) {
                mNotifyList.add(who);
                mIPv6TetheringCoordinator.addActiveDownstream(who, mode);
            }

            if (mode == IControlsTethering.STATE_TETHERED) {
                // No need to notify OffloadController just yet as there are no
                // "offload-able" prefixes to pass along. This will handled
                // when the TISM informs Tethering of its LinkProperties.
                mForwardedDownstreams.add(who);
            } else {
                mOffload.excludeDownstreamInterface(who.interfaceName());
                mForwardedDownstreams.remove(who);
            }

            // If this is a Wi-Fi interface, notify WifiManager of the active serving state.
            if (who.interfaceType() == ConnectivityManager.TETHERING_WIFI) {
                final WifiManager mgr = getWifiManager();
                final String iface = who.interfaceName();
                switch (mode) {
                    case IControlsTethering.STATE_TETHERED:
                        mgr.updateInterfaceIpState(iface, IFACE_IP_MODE_TETHERED);
                        break;
                    case IControlsTethering.STATE_LOCAL_ONLY:
                        mgr.updateInterfaceIpState(iface, IFACE_IP_MODE_LOCAL_ONLY);
                        break;
                    default:
                        Log.wtf(TAG, "Unknown active serving mode: " + mode);
                        break;
                }
            }
        }

        private void handleInterfaceServingStateInactive(TetherInterfaceStateMachine who) {
            mNotifyList.remove(who);
            mIPv6TetheringCoordinator.removeActiveDownstream(who);
            mOffload.excludeDownstreamInterface(who.interfaceName());
            mForwardedDownstreams.remove(who);

            // If this is a Wi-Fi interface, tell WifiManager of any errors.
            if (who.interfaceType() == ConnectivityManager.TETHERING_WIFI) {
                if (who.lastError() != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    getWifiManager().updateInterfaceIpState(
                            who.interfaceName(), IFACE_IP_MODE_CONFIGURATION_ERROR);
                }
            }
        }

        private void handleUpstreamNetworkMonitorCallback(int arg1, Object o) {
            if (arg1 == UpstreamNetworkMonitor.NOTIFY_LOCAL_PREFIXES) {
                mOffload.sendOffloadExemptPrefixes((Set<IpPrefix>) o);
                return;
            }

            final NetworkState ns = (NetworkState) o;

            if (ns == null || !pertainsToCurrentUpstream(ns)) {
                // TODO: In future, this is where upstream evaluation and selection
                // could be handled for notifications which include sufficient data.
                // For example, after CONNECTIVITY_ACTION listening is removed, here
                // is where we could observe a Wi-Fi network becoming available and
                // passing validation.
                if (mCurrentUpstreamIface == null) {
                    // If we have no upstream interface, try to run through upstream
                    // selection again.  If, for example, IPv4 connectivity has shown up
                    // after IPv6 (e.g., 464xlat became available) we want the chance to
                    // notice and act accordingly.
                    chooseUpstreamType(false);
                }
                return;
            }

            switch (arg1) {
                case UpstreamNetworkMonitor.EVENT_ON_AVAILABLE:
                    // The default network changed, or DUN connected
                    // before this callback was processed. Updates
                    // for the current NetworkCapabilities and
                    // LinkProperties have been requested (default
                    // request) or are being sent shortly (DUN). Do
                    // nothing until they arrive; if no updates
                    // arrive there's nothing to do.
                    break;
                case UpstreamNetworkMonitor.EVENT_ON_CAPABILITIES:
                    handleNewUpstreamNetworkState(ns);
                    break;
                case UpstreamNetworkMonitor.EVENT_ON_LINKPROPERTIES:
                    setDnsForwarders(ns.network, ns.linkProperties);
                    handleNewUpstreamNetworkState(ns);
                    break;
                case UpstreamNetworkMonitor.EVENT_ON_LOST:
                    // TODO: Re-evaluate possible upstreams. Currently upstream
                    // reevaluation is triggered via received CONNECTIVITY_ACTION
                    // broadcasts that result in being passed a
                    // TetherMasterSM.CMD_UPSTREAM_CHANGED.
                    handleNewUpstreamNetworkState(null);
                    break;
                default:
                    mLog.e("Unknown arg1 value: " + arg1);
                    break;
            }
        }

        class TetherModeAliveState extends State {
            boolean mUpstreamWanted = false;
            boolean mTryCell = true;

            @Override
            public void enter() {
                // If turning on master tether settings fails, we have already
                // transitioned to an error state; exit early.
                if (!turnOnMasterTetherSettings()) {
                    return;
                }

                mSimChange.startListening();
                mUpstreamNetworkMonitor.start();

                // TODO: De-duplicate with updateUpstreamWanted() below.
                if (upstreamWanted()) {
                    mUpstreamWanted = true;
                    mOffload.start();
                    chooseUpstreamType(true);
                    mTryCell = false;
                }
            }

            @Override
            public void exit() {
                mOffload.stop();
                mUpstreamNetworkMonitor.stop();
                mSimChange.stopListening();
                notifyDownstreamsOfNewUpstreamIface(null);
                handleNewUpstreamNetworkState(null);
            }

            private boolean updateUpstreamWanted() {
                final boolean previousUpstreamWanted = mUpstreamWanted;
                mUpstreamWanted = upstreamWanted();
                if (mUpstreamWanted != previousUpstreamWanted) {
                    if (mUpstreamWanted) {
                        mOffload.start();
                    } else {
                        mOffload.stop();
                    }
                }
                return previousUpstreamWanted;
            }

            @Override
            public boolean processMessage(Message message) {
                logMessage(this, message.what);
                boolean retValue = true;
                switch (message.what) {
                    case EVENT_IFACE_SERVING_STATE_ACTIVE: {
                        TetherInterfaceStateMachine who = (TetherInterfaceStateMachine) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode requested by " + who);
                        handleInterfaceServingStateActive(message.arg1, who);
                        who.sendMessage(TetherInterfaceStateMachine.CMD_TETHER_CONNECTION_CHANGED,
                                mCurrentUpstreamIface);
                        // If there has been a change and an upstream is now
                        // desired, kick off the selection process.
                        final boolean previousUpstreamWanted = updateUpstreamWanted();
                        if (!previousUpstreamWanted && mUpstreamWanted) {
                            chooseUpstreamType(true);
                        }
                        break;
                    }
                    case EVENT_IFACE_SERVING_STATE_INACTIVE: {
                        TetherInterfaceStateMachine who = (TetherInterfaceStateMachine) message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode unrequested by " + who);
                        handleInterfaceServingStateInactive(who);

                        if (mNotifyList.isEmpty()) {
                            // This transitions us out of TetherModeAliveState,
                            // either to InitialState or an error state.
                            turnOffMasterTetherSettings();
                            break;
                        }

                        if (DBG) {
                            Log.d(TAG, "TetherModeAlive still has " + mNotifyList.size() +
                                    " live requests:");
                            for (TetherInterfaceStateMachine o : mNotifyList) {
                                Log.d(TAG, "  " + o);
                            }
                        }
                        // If there has been a change and an upstream is no
                        // longer desired, release any mobile requests.
                        final boolean previousUpstreamWanted = updateUpstreamWanted();
                        if (previousUpstreamWanted && !mUpstreamWanted) {
                            mUpstreamNetworkMonitor.releaseMobileNetworkRequest();
                        }
                        break;
                    }
                    case EVENT_IFACE_UPDATE_LINKPROPERTIES: {
                        final LinkProperties newLp = (LinkProperties) message.obj;
                        if (message.arg1 == IControlsTethering.STATE_TETHERED) {
                            mOffload.updateDownstreamLinkProperties(newLp);
                        } else {
                            mOffload.excludeDownstreamInterface(newLp.getInterfaceName());
                        }
                        break;
                    }
                    case CMD_UPSTREAM_CHANGED:
                        updateUpstreamWanted();
                        if (!mUpstreamWanted) break;

                        // Need to try DUN immediately if Wi-Fi goes down.
                        chooseUpstreamType(true);
                        mTryCell = false;
                        break;
                    case CMD_RETRY_UPSTREAM:
                        updateUpstreamWanted();
                        if (!mUpstreamWanted) break;

                        chooseUpstreamType(mTryCell);
                        mTryCell = !mTryCell;
                        break;
                    case EVENT_UPSTREAM_CALLBACK: {
                        updateUpstreamWanted();
                        if (mUpstreamWanted) {
                            handleUpstreamNetworkMonitorCallback(message.arg1, message.obj);
                        }
                        break;
                    }
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class ErrorState extends State {
            private int mErrorNotification;

            @Override
            public boolean processMessage(Message message) {
                boolean retValue = true;
                switch (message.what) {
                    case EVENT_IFACE_SERVING_STATE_ACTIVE:
                        TetherInterfaceStateMachine who = (TetherInterfaceStateMachine) message.obj;
                        who.sendMessage(mErrorNotification);
                        break;
                    case CMD_CLEAR_ERROR:
                        mErrorNotification = ConnectivityManager.TETHER_ERROR_NO_ERROR;
                        transitionTo(mInitialState);
                        break;
                    default:
                       retValue = false;
                }
                return retValue;
            }

            void notify(int msgType) {
                mErrorNotification = msgType;
                for (TetherInterfaceStateMachine sm : mNotifyList) {
                    sm.sendMessage(msgType);
                }
            }

        }

        class SetIpForwardingEnabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setIpForwardingEnabled");
                notify(TetherInterfaceStateMachine.CMD_IP_FORWARDING_ENABLE_ERROR);
            }
        }

        class SetIpForwardingDisabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setIpForwardingDisabled");
                notify(TetherInterfaceStateMachine.CMD_IP_FORWARDING_DISABLE_ERROR);
            }
        }

        class StartTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in startTethering");
                notify(TetherInterfaceStateMachine.CMD_START_TETHERING_ERROR);
                try {
                    mNMService.setIpForwardingEnabled(false);
                } catch (Exception e) {}
            }
        }

        class StopTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in stopTethering");
                notify(TetherInterfaceStateMachine.CMD_STOP_TETHERING_ERROR);
                try {
                    mNMService.setIpForwardingEnabled(false);
                } catch (Exception e) {}
            }
        }

        class SetDnsForwardersErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setDnsForwarders");
                notify(TetherInterfaceStateMachine.CMD_SET_DNS_FORWARDERS_ERROR);
                try {
                    mNMService.stopTethering();
                } catch (Exception e) {}
                try {
                    mNMService.setIpForwardingEnabled(false);
                } catch (Exception e) {}
            }
        }

        // A wrapper class to handle multiple situations where several calls to
        // the OffloadController need to happen together.
        //
        // TODO: This suggests that the interface between OffloadController and
        // Tethering is in need of improvement. Refactor these calls into the
        // OffloadController implementation.
        class OffloadWrapper {
            public void start() {
                mOffloadController.start();
                sendOffloadExemptPrefixes();
            }

            public void stop() {
                mOffloadController.stop();
            }

            public void updateUpstreamNetworkState(NetworkState ns) {
                mOffloadController.setUpstreamLinkProperties(
                        (ns != null) ? ns.linkProperties : null);
            }

            public void updateDownstreamLinkProperties(LinkProperties newLp) {
                // Update the list of offload-exempt prefixes before adding
                // new prefixes on downstream interfaces to the offload HAL.
                sendOffloadExemptPrefixes();
                mOffloadController.notifyDownstreamLinkProperties(newLp);
            }

            public void excludeDownstreamInterface(String ifname) {
                // This and other interfaces may be in local-only hotspot mode;
                // resend all local prefixes to the OffloadController.
                sendOffloadExemptPrefixes();
                mOffloadController.removeDownstreamInterface(ifname);
            }

            public void sendOffloadExemptPrefixes() {
                sendOffloadExemptPrefixes(mUpstreamNetworkMonitor.getLocalPrefixes());
            }

            public void sendOffloadExemptPrefixes(final Set<IpPrefix> localPrefixes) {
                // Add in well-known minimum set.
                PrefixUtils.addNonForwardablePrefixes(localPrefixes);
                // Add tragically hardcoded prefixes.
                localPrefixes.add(PrefixUtils.DEFAULT_WIFI_P2P_PREFIX);

                // Maybe add prefixes or addresses for downstreams, depending on
                // the IP serving mode of each.
                for (TetherInterfaceStateMachine tism : mNotifyList) {
                    final LinkProperties lp = tism.linkProperties();

                    switch (tism.servingMode()) {
                        case IControlsTethering.STATE_UNAVAILABLE:
                        case IControlsTethering.STATE_AVAILABLE:
                            // No usable LinkProperties in these states.
                            continue;
                        case IControlsTethering.STATE_TETHERED:
                            // Only add IPv4 /32 and IPv6 /128 prefixes. The
                            // directly-connected prefixes will be sent as
                            // downstream "offload-able" prefixes.
                            for (LinkAddress addr : lp.getAllLinkAddresses()) {
                                final InetAddress ip = addr.getAddress();
                                if (ip.isLinkLocalAddress()) continue;
                                localPrefixes.add(PrefixUtils.ipAddressAsPrefix(ip));
                            }
                            break;
                        case IControlsTethering.STATE_LOCAL_ONLY:
                            // Add prefixes covering all local IPs.
                            localPrefixes.addAll(PrefixUtils.localPrefixesFrom(lp));
                            break;
                    }
                }

                mOffloadController.setLocalPrefixes(localPrefixes);
            }
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        // Binder.java closes the resource for us.
        @SuppressWarnings("resource")
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) return;

        pw.println("Tethering:");
        pw.increaseIndent();

        pw.println("Configuration:");
        pw.increaseIndent();
        final TetheringConfiguration cfg = mConfig;
        cfg.dump(pw);
        pw.decreaseIndent();

        synchronized (mPublicSync) {
            pw.println("Tether state:");
            pw.increaseIndent();
            for (int i = 0; i < mTetherStates.size(); i++) {
                final String iface = mTetherStates.keyAt(i);
                final TetherState tetherState = mTetherStates.valueAt(i);
                pw.print(iface + " - ");

                switch (tetherState.lastState) {
                    case IControlsTethering.STATE_UNAVAILABLE:
                        pw.print("UnavailableState");
                        break;
                    case IControlsTethering.STATE_AVAILABLE:
                        pw.print("AvailableState");
                        break;
                    case IControlsTethering.STATE_TETHERED:
                        pw.print("TetheredState");
                        break;
                    case IControlsTethering.STATE_LOCAL_ONLY:
                        pw.print("LocalHotspotState");
                        break;
                    default:
                        pw.print("UnknownState");
                        break;
                }
                pw.println(" - lastError = " + tetherState.lastError);
            }
            pw.println("Upstream wanted: " + upstreamWanted());
            pw.println("Current upstream interface: " + mCurrentUpstreamIface);
            pw.decreaseIndent();
        }

        pw.println("Hardware offload:");
        pw.increaseIndent();
        mOffloadController.dump(pw);
        pw.decreaseIndent();

        pw.println("Log:");
        pw.increaseIndent();
        if (argsContain(args, SHORT_ARG)) {
            pw.println("<log removed for brevity>");
        } else {
            mLog.dump(fd, pw, args);
        }
        pw.decreaseIndent();

        pw.decreaseIndent();
    }

    private static boolean argsContain(String[] args, String target) {
        for (String arg : args) {
            if (target.equals(arg)) return true;
        }
        return false;
    }

    private IControlsTethering makeControlCallback(String ifname) {
        return new IControlsTethering() {
            @Override
            public void updateInterfaceState(
                    TetherInterfaceStateMachine who, int state, int lastError) {
                notifyInterfaceStateChange(ifname, who, state, lastError);
            }

            @Override
            public void updateLinkProperties(
                    TetherInterfaceStateMachine who, LinkProperties newLp) {
                notifyLinkPropertiesChanged(ifname, who, newLp);
            }
        };
    }

    // TODO: Move into TetherMasterSM.
    private void notifyInterfaceStateChange(
            String iface, TetherInterfaceStateMachine who, int state, int error) {
        synchronized (mPublicSync) {
            final TetherState tetherState = mTetherStates.get(iface);
            if (tetherState != null && tetherState.stateMachine.equals(who)) {
                tetherState.lastState = state;
                tetherState.lastError = error;
            } else {
                if (DBG) Log.d(TAG, "got notification from stale iface " + iface);
            }
        }

        mLog.log(String.format("OBSERVED iface=%s state=%s error=%s", iface, state, error));

        try {
            // Notify that we're tethering (or not) this interface.
            // This is how data saver for instance knows if the user explicitly
            // turned on tethering (thus keeping us from being in data saver mode).
            mPolicyManager.onTetheringChanged(iface, state == IControlsTethering.STATE_TETHERED);
        } catch (RemoteException e) {
            // Not really very much we can do here.
        }

        // If TetherMasterSM is in ErrorState, TetherMasterSM stays there.
        // Thus we give a chance for TetherMasterSM to recover to InitialState
        // by sending CMD_CLEAR_ERROR
        if (error == ConnectivityManager.TETHER_ERROR_MASTER_ERROR) {
            mTetherMasterSM.sendMessage(TetherMasterSM.CMD_CLEAR_ERROR, who);
        }
        int which;
        switch (state) {
            case IControlsTethering.STATE_UNAVAILABLE:
            case IControlsTethering.STATE_AVAILABLE:
                which = TetherMasterSM.EVENT_IFACE_SERVING_STATE_INACTIVE;
                break;
            case IControlsTethering.STATE_TETHERED:
            case IControlsTethering.STATE_LOCAL_ONLY:
                which = TetherMasterSM.EVENT_IFACE_SERVING_STATE_ACTIVE;
                break;
            default:
                Log.wtf(TAG, "Unknown interface state: " + state);
                return;
        }
        mTetherMasterSM.sendMessage(which, state, 0, who);
        sendTetherStateChangedBroadcast();
    }

    private void notifyLinkPropertiesChanged(String iface, TetherInterfaceStateMachine who,
                                             LinkProperties newLp) {
        final int state;
        synchronized (mPublicSync) {
            final TetherState tetherState = mTetherStates.get(iface);
            if (tetherState != null && tetherState.stateMachine.equals(who)) {
                state = tetherState.lastState;
            } else {
                mLog.log("got notification from stale iface " + iface);
                return;
            }
        }

        mLog.log(String.format(
                "OBSERVED LinkProperties update iface=%s state=%s lp=%s",
                iface, IControlsTethering.getStateString(state), newLp));
        final int which = TetherMasterSM.EVENT_IFACE_UPDATE_LINKPROPERTIES;
        mTetherMasterSM.sendMessage(which, state, 0, newLp);
    }

    private void maybeTrackNewInterfaceLocked(final String iface) {
        // If we don't care about this type of interface, ignore.
        final int interfaceType = ifaceNameToType(iface);
        if (interfaceType == ConnectivityManager.TETHERING_INVALID) {
            mLog.log(iface + " is not a tetherable iface, ignoring");
            return;
        }
        maybeTrackNewInterfaceLocked(iface, interfaceType);
    }

    private void maybeTrackNewInterfaceLocked(final String iface, int interfaceType) {
        // If we have already started a TISM for this interface, skip.
        if (mTetherStates.containsKey(iface)) {
            mLog.log("active iface (" + iface + ") reported as added, ignoring");
            return;
        }

        mLog.log("adding TetheringInterfaceStateMachine for: " + iface);
        final TetherState tetherState = new TetherState(
                new TetherInterfaceStateMachine(
                    iface, mLooper, interfaceType, mLog, mNMService, mStatsService,
                    makeControlCallback(iface), v6OnlyTetherEnabled));
        mTetherStates.put(iface, tetherState);
        tetherState.stateMachine.start();
    }

    private void stopTrackingInterfaceLocked(final String iface) {
        final TetherState tetherState = mTetherStates.get(iface);
        if (tetherState == null) {
            mLog.log("attempting to remove unknown iface (" + iface + "), ignoring");
            return;
        }
        tetherState.stateMachine.stop();
        mLog.log("removing TetheringInterfaceStateMachine for: " + iface);
        mTetherStates.remove(iface);
    }

    private static String[] copy(String[] strarray) {
        return Arrays.copyOf(strarray, strarray.length);
    }
}

