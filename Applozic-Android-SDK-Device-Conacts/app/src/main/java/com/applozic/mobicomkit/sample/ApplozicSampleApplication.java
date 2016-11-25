package com.applozic.mobicomkit.sample;

import android.content.Context;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;

/**
 * Created by sunil on 21/3/16.
 */
public class ApplozicSampleApplication extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
      // Fabric.with(this, new Crashlytics());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

}
