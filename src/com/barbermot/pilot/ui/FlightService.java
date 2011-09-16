package com.barbermot.pilot.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.IBinder;
import android.widget.Toast;

import com.barbermot.pilot.R;
import com.barbermot.pilot.flight.FlightThread;

public class FlightService extends Service {
    
    private FlightThread flightThread;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        flightThread.start();
        return START_STICKY;
    }
    
    @Override
    public void onCreate() {
        showNotification();
        flightThread = new FlightThread(
                (SensorManager) this.getSystemService(Context.SENSOR_SERVICE),
                (LocationManager) this
                        .getSystemService(Context.LOCATION_SERVICE));
    }
    
    @Override
    public void onDestroy() {
        this.stopForeground(true);
        
        flightThread.abort();
        try {
            flightThread.join();
        } catch (InterruptedException e) {}
        
        Toast.makeText(this, R.string.flight_service_stopped,
                Toast.LENGTH_SHORT).show();
    }
    
    private void showNotification() {
        CharSequence text = getText(R.string.flight_service_running);
        
        Notification notification = new Notification(R.drawable.icon, text,
                System.currentTimeMillis());
        
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, FlightActivity.class), 0);
        
        notification.setLatestEventInfo(this,
                getText(R.string.flight_service_disable_message), text,
                contentIntent);
        
        startForeground(R.string.flight_service_running, notification);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
}
