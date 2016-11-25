package com.applozic.mobicomkit.broadcast;

import android.app.Service;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.util.Log;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;

/**
 * Created by sunil on 6/7/16.
 */
public class ApplozicListenerService extends Service {

    private static final String TAG = "ALForeground";
    MyContentObserver mObserver;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Started service......");
        try{
            mObserver = new MyContentObserver();
            getApplicationContext().getContentResolver().registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI, true, mObserver);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public int onStartCommand(final Intent intent, final int flags,
                              final int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Destroy service......");
        getApplicationContext().getContentResolver().unregisterContentObserver(mObserver);
    }


    private class MyContentObserver extends ContentObserver {

        public MyContentObserver() {
            super(null);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            MobiComUserPreference.getInstance(getApplicationContext()).setSyncContacts(true);
            Log.i(TAG, "ContentObserver is called for contacts change");
        }

    }

}

