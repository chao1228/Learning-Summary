/* Copyright (C) 2017 Tcl Corporation Limited */
/*******************************************************************************/
/*                                                            Date: 2012-11-21 */
/*                                 PRESENTATION                                */
/*                                                                             */
/*       Copyright 2012 TCT Communications Technology Holdings Limited         */
/*                                                                             */
/* This material is company confidential, cannot be reproduced in any form     */
/* without the written permission of TCL Communication Technology Holdings     */
/* Limited.                                                                    */
/*                                                                             */
/*-----------------------------------------------------------------------------*/
/* Author: bingqin.wang                                                        */
/* E-MAIL: bingqin.wang@tcl-mobile.com                                         */
/* Role  : service entitlement                                                 */
/* Reference documents:                                                        */
/*-----------------------------------------------------------------------------*/
/* Comments:                                                                   */
/* File    : /vendor/tct/source/packages/apps/serviceEntitlement/src/com/      */
/* app/serviceEntitlement/serviceEntitlementReceiver.java                   */
/* Labels  :                                                                   */
/*-----------------------------------------------------------------------------*/
/*                                                                             */
/*                General service entitlement Module                           */
/*                                                                             */
/* GENERAL DESCRIPTION                                                         */
/*   This File extends BroadcastReceiver                                       */
/*                                                                             */
/*                                                                             */
/* EXTERNALIZED FUNCTIONS                                                      */
/*                                                                             */
/*                                                                             */
/* INITIALIZATION AND SEGUENCING REQUIREMENTS                                  */
/*                                                                             */
/* REFERENCE                                                                   */
/*                                                                             */
/*=============================================================================*/
/* Modifications on Features list / Changes Request / Problem Report           */
/*-----------------------------------------------------------------------------*/
/* Modifications   (year/month/day)                                            */
/* data   |   author        |   key                 | comment (what where why) */
/*-----------------------------------------------------------------------------*/
/*12-11-21| bingqin.wang |FR346328 service entiltment | For service entitlement*/
/*<CDR-SLE-070>,<CDR-SLE-072>,<CDR-SLE-080>,<CDR-SLE-090>,<CDR-SLE-100>,       */
/* <CDR-SLE-110>                                                               */
/*=============================================================================*/
package com.app.serviceEntitlement;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.SystemProperties;

public class serviceEntitlementReceiver extends BroadcastReceiver {
    String LOG_TAG = "service_entitlement";

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        String action = intent.getAction();
        //开机启动
       if (SystemProperties.getInt("ro.ecid",-1) == 50) { // MODIFIED by yinbimin, 2017-04-25,BUG-4637074
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                Log.i(LOG_TAG, "receive ACTION_BOOT_COMPLETED");
                //[BUGFIX]-Add-BEGIN by TCTNB.Yongzhen.wang,06/05/2015,FR1016881,1016880
                //<13340Track><21.13><CDR-CON-1202> Service Layer Policy Entitlement Tethering
                context.startService(new Intent(context,serviceEntitlementService.class));
                //[BUGFIX]-Add-END by TCTNB.Yongzhen.wang
            //[DEBUG]-Modify-BEGIN by TCTNB Jianze.Dai for service entitlement,PR456807 05/24/2013
            }else if(action.equals("com.app.serviceEntitlement.servicehandler")){
                Log.i(LOG_TAG, "receive com.app.serviceEntitlement.servicehandler");
                int reqCode = intent.getIntExtra("messageID", 0);
                Log.i(LOG_TAG, "reqCode = "+reqCode);
                Intent intentServiceHandler = new Intent(context, serviceEntitlementService.class);
                intentServiceHandler.putExtra("messageID", reqCode);
                context.startService(intentServiceHandler);
            }
            //[DEBUG]-Modify-END by TCTNB Jianze.Dai for service entitlement,PR456807 05/24/2013
        } // MODIFIED by yinbimin, 2017-04-25,BUG-4637074
    }
}

