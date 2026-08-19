package com.ayush.quietlib;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

public class SMSHelper {

    private static final String TAG = "SMSHelper";

    public interface SMSCallback {
        void onSuccess();

        void onError(Exception e);
    }

    public static void sendSMS(Context context, String phoneNumber, String message, SMSCallback callback) {
        SmsManager smsManager = SmsManager.getDefault();
        String SENT = "SMS_SENT";

        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, new Intent(SENT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        callback.onSuccess();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Toast.makeText(context, "Generic Failure", Toast.LENGTH_SHORT).show();
                        callback.onError(new Exception("Generic Failure"));
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(context, "No Service", Toast.LENGTH_SHORT).show();
                        callback.onError(new Exception("No Service"));
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Toast.makeText(context, "Null PDU", Toast.LENGTH_SHORT).show();
                        callback.onError(new Exception("Null PDU"));
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Toast.makeText(context, "Radio Off", Toast.LENGTH_SHORT).show();
                        callback.onError(new Exception("Radio Off"));
                        break;
                    default:
                        Toast.makeText(context, "Unknown Error", Toast.LENGTH_SHORT).show();
                        callback.onError(new Exception("Unknown Error"));
                }

                try {
                    context.unregisterReceiver(this);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Receiver already unregistered");
                }
            }
        }, new IntentFilter(SENT));

        try {
            smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null);
        } catch (Exception e) {
            callback.onError(new Exception("Failed to send SMS: " + e.getMessage()));
        }
    }
}

