package com.barbermot.pilot;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.exception.ConnectionLostException;
import android.util.Log;

public class UltrasoundSignal extends Signal {
	
	public final String TAG = "UltrasoundSignal";
	
	public static final int MAX_RELIABLE = 367;
	
	private DigitalOutput out;
	
	public UltrasoundSignal(IOIO ioio,int pin) throws ConnectionLostException {
		super(ioio,pin);
	}
	
	@Override
	protected void setupMeasurement() throws ConnectionLostException {
		try {
			out = ioio.openDigitalOutput(pin);
			out.write(false);
			Thread.sleep(0, 2000);
			out.write(true);
			Thread.sleep(0, 5000);
			out.write(false);
		} catch (InterruptedException e) {
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}
	
	@Override
	protected Double convert(long microseconds) {
	    // The speed of sound is 340 m/s or 29 microseconds per centimeter.
	    // The ping travels out and back, so to find the distance of the
	    // object we take half of the distance traveled.
		// Log.d(TAG, "Microseconds "+microseconds);
	    double value = microseconds / 29 / 2;
	    return (value <= MAX_RELIABLE)? value : null;
	}
}
