package com.smtp.smtp;

import android.content.Intent;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

class ActivityStarterModule extends ReactContextBaseJavaModule {
    private static ReactApplicationContext reactContext;
    ActivityStarterModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
    }

    @Override
    public String getName() {
        return "ActivityStarter";
    }

    @ReactMethod
    void startNavigation(ReadableArray origin, ReadableArray destination, String userId, String chantierId, String myEtat) {
        double[] originLnglat = new double[2];
        double[] destinationLnglat = new double[2];

        originLnglat[0] = origin.getDouble(0);
        originLnglat[1] = origin.getDouble(1);

        destinationLnglat[0] = destination.getDouble(0);
        destinationLnglat[1] = destination.getDouble(1);

        Intent intent = new Intent(reactContext, Navigation.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("origin", originLnglat);
        intent.putExtra("destination", destinationLnglat);
        intent.putExtra("userId", userId);
        intent.putExtra("chantierId", chantierId);
        intent.putExtra("myEtat", myEtat);

        reactContext.startActivity(intent);
    }

    @ReactMethod
    void startRoute(ReadableArray origin, ReadableArray destination, String userId, String chantierId, String myEtat) {
        double[] originLnglat = new double[2];
        double[] destinationLnglat = new double[2];

        originLnglat[0] = origin.getDouble(0);
        originLnglat[1] = origin.getDouble(1);

        destinationLnglat[0] = destination.getDouble(0);
        destinationLnglat[1] = destination.getDouble(1);

        Intent intent = new Intent(reactContext, Navigation.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("origin", originLnglat);
        intent.putExtra("destination", destinationLnglat);
        intent.putExtra("userId", userId);
        intent.putExtra("chantierId", chantierId);
        intent.putExtra("myEtat", myEtat);

        reactContext.startActivity(intent);
    }

}
