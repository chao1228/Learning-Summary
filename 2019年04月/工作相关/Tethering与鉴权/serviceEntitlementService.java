/* Copyright (C) 2017 Tcl Corporation Limited */
/***************************************************************************************/
/*                       Date: 2012-11-21                                              */
/*                                 PRESENTATION                                        */
/*                                                                                     */
/*       Copyright 2012 TCT Communications Technology Holdings Limited                 */
/*                                                                                     */
/* This material is company confidential, cannot be reproduced in any form             */
/* without the written permission of TCL Communication Technology Holdings             */
/* Limited.                                                                            */
/*                                                                                     */
/*-------------------------------------------------------------------------------------*/
/* Author: bingqin.wang                                                                */
/* E-MAIL: bingqin.wang@tcl-mobile.com                                                 */
/* Role  : service entitlement                                                         */
/* Reference documents:                                                                */
/*-------------------------------------------------------------------------------------*/
/* Comments:                                                                           */
/* File    : /vendor/tct/source/packages/apps/serviceEntitlement/src/com/              */
/* app/serviceEntitlement/serviceEntitlementReceiver.java                           */
/* Labels  :                                                                           */
/*-------------------------------------------------------------------------------------*/
/*                                                                                     */
/*                General service entitlement Module                                   */
/*                                                                                     */
/* GENERAL DESCRIPTION                                                                 */
/*   This File extends service                                                         */
/*                                                                                     */
/*                                                                                     */
/* EXTERNALIZED FUNCTIONS                                                              */
/*                                                                                     */
/*                                                                                     */
/* INITIALIZATION AND SEGUENCING REQUIREMENTS                                          */
/*                                                                                     */
/* REFERENCE                                                                           */
/*                                                                                     */
/*=====================================================================================*/
/* Modifications on Features list / Changes Request / Problem Report                   */
/*-------------------------------------------------------------------------------------*/
/* Modifications   (year/month/day)                                                    */
/* data   |   author        |   key                 | comment (what where why)         */
/*-------------------------------------------------------------------------------------*/
/*12-11-21| bingqin.wang    |FR346328 service entiltment    | For service entitlement  */
/*<CDR-SLE-070>,<CDR-SLE-072>,<CDR-SLE-080>,<CDR-SLE-090>,<CDR-SLE-100>,<CDR-SLE-110>  */
/* ----------|----------------------|----------------------|-------------------------- */
/* 11/30/2012|LongNa                |FR342642              |PAN Tethering Entitlement  */
/*           |                      |                      |Check                      */
/* ----------|----------------------|----------------------|-------------------------- */
/* 01/28/2013|LongNa                |FR393732              |DUN Tethering Entitlement  */
/*           |                      |                      |Check                      */
/* ----------|----------------------|----------------------|-------------------------- */
/* 03/19/2013|Jianze.Dai            |PR419032              |Fix  PR419032              */
/* ----------|----------------------|----------------------|-------------------------- */
/* 04/05/2013| Jianze.Dai           |CR434832              |Remove the setting         */
/*           |                      |                      |menu for service           */
/*           |                      |                      |entitlement                */
/* ----------|----------------------|----------------------|-------------------------- */
/* 04/10/2013| Jianze.Dai           |PR435123              |Fix  PR435123              */
/* ----------|----------------------|----------------------|-------------------------- */
/* 04/19/2013| Jianze.Dai           |PR423938              |Fix  PR423938              */
/* ----------|----------------------|----------------------|-------------------------- */
/* 05/28/2013| Jianze.Dai           |PR456807              |Fix  PR456807              */
/* ----------|----------------------|----------------------|-------------------------- */
/* 07/23/2015| Yongzhen.Wang        |PR 1048810,1051226,   | Entitlement feature       */
/*           |                      |1034328               |  improvement              */
/*=====================================================================================*/
package com.app.serviceEntitlement;

import java.util.ArrayList;
import java.lang.Thread;
import java.lang.Runnable;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.util.Log;

//[FEATTURE]-Add-BEGIN by TCTNB.LongNa,11/30/2012,342642,PAN Tethering Entitlement Check
import android.os.RemoteException;
import android.os.ServiceManager;
import android.bluetooth.IBluetooth;
//[FEATTURE]-Add-END by TCTNB.LongNa

//[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,03/19/2013,PR-419032,
import java.util.Timer;
import java.util.TimerTask;
import android.telephony.TelephonyManager;
//[BUGFIX]-Add-END by TCTNB.Jianze.Dai,03/19/2013,PR-419032,

//[BUGFIX]-ADD-BEGIN by TCTNB.(Jianze.Dai),2013/04/10,PR-435123
import android.provider.Settings;
import android.net.ConnectivityManager;
//[BUGFIX]-ADD-END by TCTNB.(Jianze.Dai),2013/04/10,PR-435123

//[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
//[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
//[BUGFIX]-Add-BEGIN by TCTNB.Yongzhen.wang,07/23/2015,PR1048810,1051226,1034328,
//AT&T entitlement checking REQ for USB/Wifi tethering
import android.text.TextUtils;
//[BUGFIX]-Add-END by TCTNB.Yongzhen.wang


public class serviceEntitlementService extends Service {
    private static final int EVENT_REENTITLEMENT_USB_TETHERING_SERVICE = 1;
    private static final int EVENT_REENTITLEMENT_BLURTOOTH_TETHERING_SERVICE = 2;
    private static final int EVENT_REENTITLEMENT_MOBILE_HOTSPOT_SERVICE = 3;
    //[FEATTURE]-Add-BEGIN by TCTNB.LongNa,01/28/2013,393732,DUN Tethering Entitlement Check
    private static final int EVENT_REENTITLEMENT_BLURTOOTH_DUN_TETHERING_SERVICE = 4;
    //[FEATTURE]-Add-END by TCTNB.LongNa
    private BroadcastReceiver mTetherChangeReceiver;
    private boolean mUsbConnected;
    private boolean mMassStorageActive;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private String[] mUsbRegexs;
    private String[] mWifiRegexs;
    private String[] mBluetoothRegexs;
    private WifiManager mWifiManager;

    //[FEATTURE]-Add-BEGIN by TCTNB.LongNa,11/30/2012,342642,PAN Tethering Entitlement Check
    private IBluetooth mBluetoothService;
    //[FEATTURE]-Add-END by TCTNB.LongNa

    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,03/19/2013,PR-419032,
    private int timerCount = 0;
    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,03/19/2013,PR-419032,

    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,04/19/2013,PR-423938
    private int usbTetheringTimerCount = 0;
    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,04/19/2013,PR-423938

    //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
    private WakeLock mWakeLock;
    //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013

    public static final long reEntitlemetInterval = 24*60*60*1000; // MODIFIED by sunyandong, 2017-03-09,BUG-4280174

    public static final String LOG_TAG = "Service_Entitlement";
    public static final String EXTRA_AVAILABLE_TETHER = "availableArray";
    public static final String EXTRA_ACTIVE_TETHER = "activeArray";
    public static final String EXTRA_ERRORED_TETHER = "erroredArray";

    public long mUsbTetheringStartTime = 0;
    public long mWifiHotspotStartTime = 0;
    public long mBluetoothTetheringStartTime = 0;
    //[FEATTURE]-Add-BEGIN by TCTNB.LongNa,01/28/2013,393732,DUN Tethering Entitlement Check
    public long mBluetoothDunTetheringStartTime = 0;

    public static final String DUN_STATECHANGE_INTENT =
            "com.android.bluetooth.dun.statechanged";
    private String DunIface = "BluetoothDun";
    private String mDunIface;
    //[FEATTURE]-Add-END by TCTNB.LongNa
    public static final String ACTION_SERVICE_ENTITLEMENT = "action.service.entitlement";
    public static final String ACTION_SERVICE_ENTITLEMENT_RESULT = "action.service.entitlement.result";
    public static final String CHECK_IFACE = "check.iface";
    public static final String CHECK_RESULT = "check.result";
    public static final String ACTION_CONNECTIVITY_APN_FAIL = "action.connectivity.apn.failed"; // MODIFIED by zhouchenglin, 2017-12-08,BUG-5719037

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        Log.i(LOG_TAG, "service entitlement start-------------www");
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mUsbRegexs = cm.getTetherableUsbRegexs();
        mWifiRegexs = cm.getTetherableWifiRegexs();
        mBluetoothRegexs = cm.getTetherableBluetoothRegexs();
        mUsbConnected = false;
        mMassStorageActive = false;
        HandlerThread thread = new HandlerThread("serviceEntitlementService",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        /*[FEATTURE]-Add-BEGIN by TCTNB.LongNa,11/30/2012,342642,PAN Tethering Entitlement Check

        IBinder b = ServiceManager.getService(Context.BLUETOOTH_SERVICE);
        if (b == null) {
            throw new RuntimeException("Bluetooth service not available");
        }
        mBluetoothService = IBluetooth.Stub.asInterface(b);
        //[FEATTURE]-Add-END by TCTNB.LongNa*/

        //[BUGFIX]-Add-BEGIN by TCTNB.Yongzhen.wang,06/05/2015,FR1016881,1016880
        //<13340Track><21.13><CDR-CON-1202> Service Layer Policy Entitlement Tethering
        mTetherChangeReceiver = new TetherChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        this.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        this.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        this.registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        //[FEATTURE]-Add-BEGIN by TCTNB.LongNa,01/28/2013,393732,DUN Tethering Entitlement Check
        filter.addAction(DUN_STATECHANGE_INTENT);
        //[FEATTURE]-Add-END by TCTNB.LongNa
        this.registerReceiver(mTetherChangeReceiver, filter);
        filter = new IntentFilter();
        filter.addAction(ACTION_SERVICE_ENTITLEMENT);
        this.registerReceiver(mTetherChangeReceiver, filter);
        //[BUGFIX]-Add-END by TCTNB.Yongzhen.wang
        /* MODIFIED-BEGIN by zhouchenglin, 2017-12-08,BUG-5719037*/
        filter = new IntentFilter();
        filter.addAction(ACTION_CONNECTIVITY_APN_FAIL);
        this.registerReceiver(mTetherChangeReceiver, filter);
        /* MODIFIED-END by zhouchenglin,BUG-5719037*/
    }

    //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
       Log.i(LOG_TAG,"onStartCommand()");
       if (intent != null && intent.hasExtra("messageID")) {
          int messageID = intent.getIntExtra("messageID",0);
          Log.i(LOG_TAG,"cancelAlarm");
          cancelAlarm(this,"com.app.serviceEntitlement.servicehandler",messageID);

          Log.i(LOG_TAG,"onStartCommand() messageID = " + messageID);
          mServiceHandler.sendEmptyMessage(messageID);
       }
       return Service.START_STICKY;
    }
    //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        this.unregisterReceiver(mTetherChangeReceiver);
        mTetherChangeReceiver = null;
        //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
        }
        //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            String[] tetheredIface = cm.getTetheredIfaces();
            //boolean isEntitlement = false;
            switch (msg.what) {
            case EVENT_REENTITLEMENT_USB_TETHERING_SERVICE:
                Log.i(LOG_TAG, "re-entitlement usb tethering service");
                String usbIface = findIface(tetheredIface, mUsbRegexs);
              /*  if (mUsbTetheringStartTime > 0)
                    isEntitlement = SystemClock.elapsedRealtime() - mUsbTetheringStartTime >= reEntitlemetInterval - 10*60*1000 ? true : false;*/
                if (usbIface != null /*&& isEntitlement*/) {
                    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    Log.i(LOG_TAG, "UsbHandle: Check if Data is connected!");
                    if(0 == usbTetheringTimerCount){
                       processUsbHandle(HttpUtils.TETHERING_SERVICE,getApplicationContext(),usbIface);
                    }
                    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    //entitlementHandle usbhandle = new entitlementHandle(
                    //        getApplicationContext(),
                    //        HttpUtils.TETHERING_SERVICE, usbIface);
                    // usbhandle.process();
                } /*else {
                    mUsbTetheringStartTime = 0;
                }*/
                break;

            case EVENT_REENTITLEMENT_BLURTOOTH_TETHERING_SERVICE:
                Log.i(LOG_TAG, "re-entitlement bluetooth tethering service");
                String bluetoothIface = findIface(tetheredIface,
                        mBluetoothRegexs);
                Log.i(LOG_TAG, "bluetoothIface =" + bluetoothIface
                        + "mBluetoothTetheringStartTime="
                        + mBluetoothTetheringStartTime);
               /* if (mBluetoothTetheringStartTime > 0) {
                    isEntitlement = SystemClock.elapsedRealtime() - mBluetoothTetheringStartTime >= reEntitlemetInterval -10*60*1000? true : false;
                    Log.i(LOG_TAG, "SystemClock.elapsedRealtime() ="
                            + SystemClock.elapsedRealtime() + "isEntitlement = "
                            + isEntitlement);
                }*/
                if (bluetoothIface != null /*&& isEntitlement*/) {
                    entitlementHandle bthandle = new entitlementHandle(
                            getApplicationContext(),
                            HttpUtils.TETHERING_SERVICE, bluetoothIface);
                    bthandle.process();
                }
                break;

            case EVENT_REENTITLEMENT_MOBILE_HOTSPOT_SERVICE:
                Log.i(LOG_TAG, "re-entitlement wifi hotspot service");
                String wifiIface = findIface(tetheredIface, mWifiRegexs);
                Log.i(LOG_TAG, "wifiIface =" + wifiIface
                        + "mWifiHotspotStartTime=" + mWifiHotspotStartTime);
              /*  if (mWifiHotspotStartTime > 0) {
                    isEntitlement = SystemClock.elapsedRealtime() - mWifiHotspotStartTime >= reEntitlemetInterval-10*60*1000 ? true : false;
                    Log.i(LOG_TAG, "SystemClock.elapsedRealtime() ="
                            + SystemClock.elapsedRealtime() + "isEntitlement = "
                            + isEntitlement);
                }*/
                if (wifiIface != null /*&& isEntitlement*/) {
                    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    if(0 == timerCount){
                      processWifiHandle(HttpUtils.MOBILE_HOTSPOT_SERVICE,getApplicationContext(),wifiIface);
                    }
                    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    //entitlementHandle wifihandle = new entitlementHandle(
                    //        getApplicationContext(),
                    //        HttpUtils.MOBILE_HOTSPOT_SERVICE, wifiIface);
                    //wifihandle.process();
                }/* else {
                    mWifiHotspotStartTime = 0;
                }*/
                break;
            //[FEATTURE]-Add-BEGIN by TCTNB.LongNa,01/28/2013,393732,DUN Tethering Entitlement Check
            case EVENT_REENTITLEMENT_BLURTOOTH_DUN_TETHERING_SERVICE:
                /*if (mBluetoothDunTetheringStartTime > 0) {
                    isEntitlement = SystemClock.elapsedRealtime() - mBluetoothDunTetheringStartTime >= reEntitlemetInterval - 10*60*1000? true : false;
                    Log.i(LOG_TAG, "SystemClock.elapsedRealtime() ="
                            + SystemClock.elapsedRealtime() + "isEntitlement = "
                            + isEntitlement);
                }*/
                if (mDunIface != null /*&& isEntitlement*/) {
                    entitlementHandle bthandle = new entitlementHandle(
                            getApplicationContext(),
                            HttpUtils.TETHERING_SERVICE, mDunIface);
                    bthandle.process();
                } /*else {
                    mBluetoothDunTetheringStartTime = 0;
                }*/
                break;
            //[FEATTURE]-Add-END by TCTNB.LongNa

            default:

            }
        }
    }

    public String findIface(String[] ifaces, String[] regexes) {
        for (String iface : ifaces) {
            if (matchRegex(iface, regexes)) {
                return iface;
            }
        }
        return null;
    }

    public boolean matchRegex(String iface, String[] regexes) {
        for (String regex : regexes) {
            if (iface.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private void untether(String Iface) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.untether(Iface);
    }

    //[BUGFIX]-Add-BEGIN by TCTNB.Yongzhen.wang,07/23/2015,PR1048810,1051226,1034328,
    //AT&T entitlement checking REQ for USB/Wifi tethering
    private boolean isAttSimCard(){
      boolean isAttCard = false;
      String simNumeric = TelephonyManager.getDefault().getSimOperator();
      if(!TextUtils.isEmpty(simNumeric)){
         if(simNumeric.equals("311180") ||simNumeric.equals("310410")
            ||simNumeric.equals("310150") ||simNumeric.equals("310170")
            ||simNumeric.equals("310380") ||simNumeric.equals("310560")
            ||simNumeric.equals("310680") ||simNumeric.equals("310070")
            ||simNumeric.equals("313100") ||simNumeric.equals("312670") // MODIFIED by jiang.li, 2018-05-07,BUG-6293613
            ||simNumeric.equals("310090")){
            isAttCard = true;
         }
      }
      return isAttCard;
    }

    private boolean matchTetheredIface(String iface, String ifaceName){
       boolean isMatched = false;
       ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
       String[] tetheredIface = cm.getTetheredIfaces();
       for (String tetheredNwIface : tetheredIface) {
         if (iface.equals(tetheredNwIface)){
             if((tetheredNwIface != null) && (tetheredNwIface.matches(ifaceName))){
                isMatched = true;
                break;
             }
          }
       }
       return isMatched;
    }

    public int checkPermission(Context context, int serviceType) {
        int response = -1;
        //The entitlement check will be performed
        response = HttpUtils.http_get(serviceType);
        if (response == 200) {
           Log.i(LOG_TAG, "Http 200, entitlement pass");
        } else if (response == 403) {
           Log.i(LOG_TAG, "Http 403");
        } else {
           //[BUGFIX]-ADD-BEGIN by TCTNB.(Jianze.Dai),2013/04/10,PR-435123
           final ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
           int failCause = Settings.Secure.getInt(context.getContentResolver(),"DataConnection_FailCause",0);
           if((true == connMgr.getMobileDataEnabled())
                && 9 == failCause){//If fail cause was "SERVICE_OPTION_NOT_SUBSCRIBED",the ordinal number is defined in DataConnection.FailCause
                   response = 403;
                }
                //[BUGFIX]-ADD-END by TCTNB.(Jianze.Dai),2013/04/10,PR-435123

           Log.i(LOG_TAG, "other HTTP Error:" + response);
       }

       return response;
    }

    public class entitlementHandle implements Runnable {
        private Thread mThread;
        public Context mContext;
        public int mServiceType;
        public String mIface;

        public entitlementHandle(Context context, int serviceType, String Iface) {
            mContext = context;
            mServiceType = serviceType;
            mIface = Iface;
        }

        public void process() {
            mThread = new Thread(this, "entitlementHandle");
            mThread.start();
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            Context context = mContext;
            int serviceType = mServiceType;
            String Iface = mIface;

            //Tethering can not be enabled if card is absent
            boolean isCardPresent = TelephonyManager.getDefault().hasIccCard();
            Log.i(LOG_TAG, "isCardPresent:"+isCardPresent +" isAttSimCard: "+isAttSimCard());

            if(!isCardPresent ||isAttSimCard()){
               int http_response = -1;
               boolean permit = false;

               //Perform entitlement checking and present its result to user
               if(!isCardPresent){
                   //no card, don't check
                   http_response = -1;
               }else{
                   http_response = checkPermission(context, serviceType);
               }

               //entitlement checking fails, then untether availabe interface
               if(http_response != 200){
                    if(serviceType == HttpUtils.MOBILE_HOTSPOT_SERVICE){
                        mWifiManager.stopSoftAp(); // MODIFIED by chao.hu, 2018-06-05,BUG-6379404
                    }else{
                       untether(Iface);
                    }
               }

               //if entitlement checking is successful, then Periodic Authorization should be done
               //If the service session is active for more than 24 hours, entitlement shall be
               //checked at 24 hour increments after the session is initiated
               //see AT&T Req <CDR-SLE-072>
               if(http_response == 200){
                  permit = true;
                  int event = -1;
                  switch (serviceType) {
                    case HttpUtils.TETHERING_SERVICE:
                        if (matchRegex(Iface, mUsbRegexs)) {
                            event = EVENT_REENTITLEMENT_USB_TETHERING_SERVICE;
                            mUsbTetheringStartTime = SystemClock.elapsedRealtime();
                        } else if (matchRegex(Iface, mBluetoothRegexs)) {
                            event = EVENT_REENTITLEMENT_BLURTOOTH_TETHERING_SERVICE;
                            mBluetoothTetheringStartTime = SystemClock.elapsedRealtime();
                        }
                        //[FEATTURE]-Add-BEGIN by TCTNB.LongNa,01/28/2013,393732,DUN Tethering Entitlement Check
                        else if (Iface.equals(DunIface)) {
                            event = EVENT_REENTITLEMENT_BLURTOOTH_DUN_TETHERING_SERVICE;
                            mBluetoothDunTetheringStartTime = SystemClock.elapsedRealtime();
                        }
                        //[FEATTURE]-Add-END by TCTNB.LongNa
                        break;
                    case HttpUtils.MOBILE_HOTSPOT_SERVICE:
                        event = EVENT_REENTITLEMENT_MOBILE_HOTSPOT_SERVICE;
                        mWifiHotspotStartTime = SystemClock.elapsedRealtime();
                        break;
                    default:
                        break;

                    }
                    if(mServiceHandler.hasMessages(event)){
                        Log.i(LOG_TAG,"a old message "+event+" is queued, del it ");
                        mServiceHandler.removeMessages(event);
                    }

                    //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
                    Log.i(LOG_TAG,"cancelAlarm");
                    cancelAlarm(mContext,"com.app.serviceEntitlement.servicehandler",event);

                    long alarmTimer = reEntitlemetInterval + SystemClock.elapsedRealtime();
                    Log.i(LOG_TAG,"setAlarm = " + alarmTimer);

                    setAlarm(mContext, alarmTimer,"com.app.serviceEntitlement.servicehandler",event);
                    //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
                    Log.i(LOG_TAG, "send reEntitlement event = " + event
                            + "Iface = " + Iface + " reEntitlemetInterval = " + reEntitlemetInterval);
                }else{
                   //if(!isCardPresent || tetheringAction.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)){
                   Intent intent1 = new Intent(context, alertActivity.class);
                   intent1.putExtra("HTTP_RESPONSE", http_response);
                   //[BUGFIX]-Mod-BEGIN by TCTNB.Zhang Jinbo, 2013.03.18, PR-423150,change string as ergo
                   intent1.putExtra("SERVICE_TYPE", serviceType);
                   //[BUGFIX]-Mod-END by TCTNB.Zhang Jinbo, 2013.03.18, PR-423150,change string as ergo
                   intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                   startActivity(intent1);
                   //}
                }

                //report the result of entitlement checking to tethering module(tethering.java)
                Intent intent2 = new Intent(ACTION_SERVICE_ENTITLEMENT_RESULT);
                intent2.putExtra(CHECK_IFACE,Iface);
                intent2.putExtra(CHECK_RESULT, permit);
                mContext.sendStickyBroadcast(intent2);
            }else{
                Log.i(LOG_TAG, "Non AT&T card,don't need to perform entitlement checking!");
            }
        }
    }
    //[BUGFIX]-Add-END by TCTNB.Yongzhen.wang

    private class TetherChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int serviceType = -1;
            String action = intent.getAction();
            Log.i(LOG_TAG, "onReceive,2.18,action = " + action);
            //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,CR434832,04/05/2013
            int needCheck =  Settings.Secure.getInt(context.getContentResolver(),
                "NEED_SERVICE_ENTITLEMENT", 1);
            //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,CR434832,04/05/2013
            if(needCheck == 0)
                return;

            if (action.equals(ACTION_SERVICE_ENTITLEMENT)) {
                String iface = intent.getStringExtra(CHECK_IFACE);
                if(matchRegex(iface,mUsbRegexs)){
                    serviceType = HttpUtils.TETHERING_SERVICE;
                    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    Log.i(LOG_TAG, "UsbHandle: Check if Data is connected!");
                    if(0 == usbTetheringTimerCount){
                      processUsbHandle(serviceType,context,iface);
                    }
                    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    //entitlementHandle usbhandle = new entitlementHandle(
                    //        context, serviceType, iface);
                    //usbhandle.process();
                }else if (matchRegex(iface,mWifiRegexs)){
                    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,03/18/2013,PR-419032
                    Log.i(LOG_TAG, "Check if Data is connected!");
                    serviceType = HttpUtils.MOBILE_HOTSPOT_SERVICE;
                    if(0 == timerCount){
                      processWifiHandle(serviceType,context,iface);
                    }
                    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,03/18/2013,PR-419032

//                  serviceType = HttpUtils.MOBILE_HOTSPOT_SERVICE;
//                  entitlementHandle wifihandle = new entitlementHandle(
//                          context, serviceType, iface);
//                  wifihandle.process();
                }else if  (matchRegex(iface,mBluetoothRegexs)){
                    serviceType = HttpUtils.TETHERING_SERVICE;
                    entitlementHandle bthandle = new entitlementHandle(context,
                            serviceType, iface);
                    bthandle.process();
                }else if  (iface.equals(DunIface)){
                 serviceType = HttpUtils.TETHERING_SERVICE;
                    entitlementHandle bthandle = new entitlementHandle(context,
                            serviceType, iface);
                    bthandle.process();
                }
            }else if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                IBinder b = ServiceManager.getService(Context.CONNECTIVITY_SERVICE);
                IConnectivityManager cm = IConnectivityManager.Stub.asInterface(b);
                int type = ConnectivityManager.TYPE_WIFI;
                NetworkInfo info = null;
                try {
                    info = cm.getNetworkInfo(type);
                }catch (RemoteException e) { }
                if (info == null ||(info!=null && info.isConnected())) {
                    return;
                }

                // TODO - this should understand the interface types
                ArrayList<String> available = intent
                        .getStringArrayListExtra(EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent
                        .getStringArrayListExtra(EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent
                        .getStringArrayListExtra(EXTRA_ERRORED_TETHER);

                //[FEATURE]-Add-BEGIN by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
                if(available!=null&&active!=null&&errored!=null){
                      Log.i(LOG_TAG, "tether state change action received"
                        + available.size() + ", " + active.size() + ", "
                        + errored.size());
                }
                //[FEATURE]-Add-END by chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
                String usbIface =null;
                String wifiIface=null;
                String bluetoothIface=null;
                if(active!=null){
                      usbIface = findIface(active.toArray(new String[active.size()]),mUsbRegexs);
                      wifiIface = findIface(active.toArray(new String[active.size()]),mWifiRegexs);
                      bluetoothIface = findIface(active.toArray(new String[active.size()]),mBluetoothRegexs);
                }
                boolean permission = false;


                //[BUGFIX]-Add-BEGIN by TCTNB.Yongzhen.wang,07/23/2015,PR1048810,1051226,1034328,
                //AT&T entitlement checking REQ for USB/Wifi tethering
                //entitlement checking is necessary when card is AT&T one or absent
                //boolean isCardPresent = TelephonyManager.getDefault().hasIccCard();
                //boolean isToBeChecked = !isCardPresent|| isAttSimCard();
                boolean isToBeChecked = (isAttSimCard() == true) && (isDataConnected(context));
                try{
                    if(usbIface!=null)
                        permission = cm.getPermission(usbIface);
                }catch (RemoteException e) { }

                if (mUsbConnected && !mMassStorageActive && usbIface != null && !permission) {
                    serviceType = HttpUtils.TETHERING_SERVICE;
                    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    Log.i(LOG_TAG, "UsbHandle: Check if Data is connected!");
                    if((0 == usbTetheringTimerCount) && isToBeChecked){
                      processUsbHandle(serviceType,context,usbIface);
                    }
                    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,04/19/2013,PR-423938

                  //  entitlementHandle usbhandle = new entitlementHandle(
                  //          context, serviceType, usbIface);
                  //  usbhandle.process();
                }
                try{
                    if(wifiIface!=null)
                        permission = cm.getPermission(wifiIface);
                }catch (RemoteException e) { }
                if (wifiIface != null && !permission) {
                    serviceType = HttpUtils.MOBILE_HOTSPOT_SERVICE;
                    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    if((0 == timerCount)&& isToBeChecked){
                      processWifiHandle(serviceType,context,wifiIface);
                    }
                    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,04/19/2013,PR-423938
                    //entitlementHandle wifihandle = new entitlementHandle(
                    //        context, serviceType, wifiIface);
                    //wifihandle.process();
                }
                //[BUGFIX]-Add-END by TCTNB.Yongzhen.wang
                try{
                    if(bluetoothIface!=null)
                        permission = cm.getPermission(bluetoothIface);
                }catch (RemoteException e) { }

                if (bluetoothIface != null && !permission) {
                    serviceType = HttpUtils.TETHERING_SERVICE;
                    entitlementHandle bthandle = new entitlementHandle(context,
                            serviceType, bluetoothIface);
                    bthandle.process();
                }
            /* MODIFIED-BEGIN by zhouchenglin, 2017-12-08,BUG-5719037*/
            } else if(action.equals(ACTION_CONNECTIVITY_APN_FAIL)){
                 Intent i = new Intent(context,alertActivity.class);
                 i.putExtra("HTTP_RESPONSE", alertActivity.ATT_HOTSPOT_APN_FAILED);
                 i.putExtra("SERVICE_TYPE", 1);
                 i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 startActivity(i);
                 /* MODIFIED-END by zhouchenglin,BUG-5719037*/
            } else if (action.equals(Intent.ACTION_MEDIA_SHARED)) {
                Log.i(LOG_TAG, "action media shared received");
                mMassStorageActive = true;
            } else if (action.equals(Intent.ACTION_MEDIA_UNSHARED)) {
                Log.i(LOG_TAG, "action media unshared received");
                mMassStorageActive = false;
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                Log.i(LOG_TAG, "action usb connected ");
                mUsbConnected = intent.getBooleanExtra(
                        UsbManager.USB_CONNECTED, false);
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                Log.i(LOG_TAG, "action bluetooth state changed received.");
            }
            //[FEATTURE]-Add-BEGIN by TCTNB.LongNa,01/28/2013,393732,DUN Tethering Entitlement Check
            else if (action.equals(DUN_STATECHANGE_INTENT)) {
                int DunState = intent.getIntExtra("state", 0);
                if (DunState == 2) {
                    mDunIface = DunIface;
                } else {
                    mDunIface = null;
                }
            }
            //[FEATTURE]-Add-END by TCTNB.LongNa
        }
    }

    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,03/19/2013,PR-419032
    private static boolean isDataConnected(Context context) {
        final ConnectivityManager connMgr = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        TelephonyManager tmgr = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        int dataState = tmgr.getDataState();
        Log.v(LOG_TAG, "getDataState = " + dataState);
        if (dataState == TelephonyManager.DATA_CONNECTED) {
            return true;
        }
        return false;
    }

    private void processWifiHandle(int serviceType,Context context,String iface) {
        final int subServiceType =serviceType;
        final Context subContext =context;
        final String subIface = iface;

        //Set the interval as 2 seconds
        timerCount = timerCount +2;
        Timer timer = new Timer();

        //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
        //Wake up the system once it going to sleep mode
        if(mWakeLock == null){
              Log.i(LOG_TAG, "wakeUpSystem()");
              PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
              mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wake Up");
              mWakeLock.acquire();
        }
        //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/24/2013

        //If timerCount>10(it means data is connected or retry timeout),launch the process of wifi handler
        if(timerCount>10){
            Log.i(LOG_TAG, "processWifiHandle: Launch the entitlementHandle process! timerCount = " + timerCount);
            timerCount = 0;

            //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
            if (mWakeLock != null && mWakeLock.isHeld()) {
               Log.i(LOG_TAG, "Release WakeLock");
               mWakeLock.release();
               mWakeLock = null;
            }
            //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013

            entitlementHandle wifihandle = new entitlementHandle(
               subContext, subServiceType, subIface);
            wifihandle.process();
            timer.cancel();
            return;
        }

        //If data was connected,set retry finished,and still need wait some second to avoid unsteady data connection.
        if(isDataConnected(subContext)){
            timerCount = 10;
        }

        TimerTask task = new TimerTask(){
            public void run(){
                processWifiHandle(subServiceType,subContext,subIface);
            }
        };
        //Check if the data was connected in every 2 seconds.
        timer.schedule(task,2000);
    }
    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,03/19/2013,PR-419032

    //[BUGFIX]-Add-BEGIN by TCTNB.Jianze.Dai,04/19/2013,PR-423938
    private void processUsbHandle(int serviceType,Context context,String iface) {
        final int subServiceType =serviceType;
        final Context subContext =context;
        final String subIface = iface;

        //Set the interval as 4 seconds
        usbTetheringTimerCount = usbTetheringTimerCount +4;
        Timer timer = new Timer();

        //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/24/2013
        //Wake up the system once it going to sleep mode
        if(mWakeLock == null){
              PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
              mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Wake Up");
              mWakeLock.acquire();
        }
        //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/24/2013

        //If timerCount>10(it means data is connected or retry timeout),launch the process of usb handler
        if(usbTetheringTimerCount>10){
            Log.i(LOG_TAG, "processUsbHandle:Launch the entitlementHandle process! usbTetheringTimerCount = " + usbTetheringTimerCount);
            usbTetheringTimerCount = 0;

            //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/24/2013
            if (mWakeLock != null && mWakeLock.isHeld()) {
               mWakeLock.release();
               mWakeLock = null;
            }
            //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/24/2013

            //Check if WIFI is connected,if WIFI is connected,do not need launch entitlement check process
            final ConnectivityManager connMgr = (ConnectivityManager) context
                 .getSystemService(Context.CONNECTIVITY_SERVICE);
            final android.net.NetworkInfo wifiInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (null != wifiInfo && wifiInfo.isConnected()) {
               Log.i(LOG_TAG, "processUsbHandle: WIFI is connected");
               return;
            }
            Log.i(LOG_TAG, "processUsbHandle: WIFI is not connected");

            entitlementHandle usbhandle = new entitlementHandle(
               subContext, subServiceType, subIface);
            usbhandle.process();
            timer.cancel();
            return;
        }

        //If data was connected,set retry finished,and still need wait some second to avoid unsteady data connection.
        if(isDataConnected(subContext)){
            usbTetheringTimerCount = 10;
        }

        TimerTask task = new TimerTask(){
            public void run(){
                processUsbHandle(subServiceType,subContext,subIface);
            }
        };
        //Check if the data was connected in every 4 seconds.
        timer.schedule(task,4000);
    }
    //[BUGFIX]-Add-END by TCTNB.Jianze.Dai,04/19/2013,PR-423938

    //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
    /**
     * set alarm
     *
     * @param: Context context:
     *         long   time:
     *         String action:
     *         int reqcode:
     * @return: none
     */
    private static void setAlarm(Context context, long time, String action, int reqCode) {
        PendingIntent pi;
        AlarmManager am;
        Intent intentAlarm = new Intent(action);
        intentAlarm.setPackage("com.app.serviceEntitlement");////[FEATURE] chenglin.zhou,2017-11-03,task-5558976, service layer poliy entitlement
        intentAlarm.putExtra("messageID", reqCode);
        pi = PendingIntent.getBroadcast(context, reqCode, intentAlarm, PendingIntent.FLAG_CANCEL_CURRENT);
        am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, time, pi); //chenglin.zhou,2017-11-15,task- 5598565, service layer poliy entitlement
    }

    /**
     * cancel alarm
     *
     * @param: Context context:
     *         String action:
     *         int reqcode:
     * @return: none
     */
    private static void cancelAlarm(Context context, String action, int reqCode) {
        PendingIntent pi;
        AlarmManager am;
        pi = PendingIntent.getBroadcast(context, reqCode, new Intent(action), PendingIntent.FLAG_CANCEL_CURRENT);
        am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
    }
    //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/28/2013
}

