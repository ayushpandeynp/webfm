package com.ayush.quietlib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.ayush.quietlib.rfm.C;
import com.ayush.quietlib.rfm.service.FMService;

public class autostart extends BroadcastReceiver
{
    public void onReceive(Context context, Intent arg1)
    {
        Intent intent = new Intent(context, FMService.class).setAction(C.Command.INSTALL).putExtras(new Bundle());
        context.startForegroundService(intent);

        Log.i("Autostart", "FMService started");
    }
}