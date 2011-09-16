package com.barbermot.pilot.signal;

import ioio.lib.api.exception.ConnectionLostException;

import java.util.EnumMap;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

class GpsSignal implements LocationListener, Runnable {
    
    public static final String TAG = "GpsSignal";
    
    private Location           last;
    private int                minTime;
    private Looper             looper;
    
    public enum Type {
        HEIGHT, LAT, LON, LOCATION
    };
    
    EnumMap<Type, SignalListener> listenerMap;
    LocationManager               manager;
    
    public GpsSignal(LocationManager manager, SignalListener height,
            SignalListener lat, SignalListener lon, SignalListener location,
            int minTime) {
        
        this.manager = manager;
        this.minTime = minTime;
        
        listenerMap = new EnumMap<Type, SignalListener>(Type.class);
        listenerMap.put(Type.HEIGHT, height);
        listenerMap.put(Type.LAT, lat);
        listenerMap.put(Type.LON, lon);
        listenerMap.put(Type.LOCATION, location);
    }
    
    @Override
    public void run() {
        looper = Looper.myLooper();
        Looper.prepare();
        
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime,
                0, GpsSignal.this);
        
        Looper.loop();
    }
    
    public void abort() {
        looper.quit();
        manager.removeUpdates(this);
    }
    
    @Override
    public void onLocationChanged(Location location) {
        long time = location.getTime();
        if (last != null) {
            Log.d(TAG, "Bearing: " + location.bearingTo(last) + ", distance: "
                    + location.distanceTo(last));
            Log.d(TAG,
                    "Altitude: " + location.getAltitude() + ", lon: "
                            + location.getLatitude() + ", lat: "
                            + location.getLongitude());
            Log.d(TAG, "Accuracy: " + location.getAccuracy());
        }
        
        last = location;
        
        try {
            listenerMap.get(Type.HEIGHT).update((float) location.getAltitude(),
                    time);
            listenerMap.get(Type.LAT).update((float) location.getLatitude(),
                    time);
            listenerMap.get(Type.LON).update((float) location.getLongitude(),
                    time);
        } catch (ConnectionLostException e) {
            e.printStackTrace();
        }
        
    }
    
    @Override
    public void onProviderDisabled(String provider) {}
    
    @Override
    public void onProviderEnabled(String provider) {}
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
