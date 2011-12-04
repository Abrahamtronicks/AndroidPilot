package com.barbermot.pilot.flight;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.IOIOFactory;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.api.exception.IncompatibilityException;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.hardware.SensorManager;
import android.location.LocationManager;

import com.barbermot.pilot.builder.BuildException;
import com.barbermot.pilot.builder.FlightBuilder;

public class FlightThread extends Thread {
    
    private static final Logger logger    = Logger.getLogger("FlightThread");
    
    protected IOIO              ioio;
    private DigitalOutput       led;
    private boolean             abort     = false;
    private boolean             connected = true;
    
    private SensorManager       sensorManager;
    private LocationManager     locationManager;
    
    private FlightComputer      computer;
    private List<Future<?>>     handles;
    
    public FlightThread(SensorManager sensorManager,
            LocationManager locationManager) {
        this.sensorManager = sensorManager;
        this.locationManager = locationManager;
    }
    
    @Override
    public final void run() {
        while (true) {
            try {
                synchronized (this) {
                    if (abort) {
                        break;
                    }
                    ioio = IOIOFactory.create();
                }
                ioio.waitForConnect();
                connected = true;
                logger.info("ioio is connected.");
                setup();
                while (!abort) {
                    loop();
                }
                ioio.disconnect();
                logger.info("ioio is disconnected.");
            } catch (ConnectionLostException e) {
                if (abort) {
                    break;
                }
            } catch (IncompatibilityException e) {
                logger.log(Level.SEVERE, "Incompatible IOIO firmware", e);
                // nothing to do - just wait until physical disconnection
                try {
                    ioio.waitForDisconnect();
                } catch (InterruptedException e1) {
                    ioio.disconnect();
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected exception caught", e);
                ioio.disconnect();
                break;
            } finally {
                try {
                    if (ioio != null) {
                        ioio.waitForDisconnect();
                    }
                } catch (InterruptedException e) {}
            }
        }
    }
    
    public synchronized final void abort() {
        logger.info("Abort requested.");
        abort = true;
        if (ioio != null) {
            ioio.disconnect();
        }
        if (connected) {
            interrupt();
        }
    }
    
    private void setup() throws ConnectionLostException {
        try {
            FlightBuilder builder = new FlightBuilder();
            computer = builder
                    .getComputer(ioio, sensorManager, locationManager);
            handles = builder.getFutures();
            led = ioio.openDigitalOutput(0);
        } catch (BuildException e) {
            e.printStackTrace();
        }
        logger.info("Setup complete.");
    }
    
    private void loop() throws ConnectionLostException {
        led.write(false);
        
        for (Future<?> f : handles) {
            try {
                f.get();
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "Exception caught.", e);
            } catch (ExecutionException e) {
                Throwable ex = e;
                while (ex != null) {
                    logger.log(Level.SEVERE, "Exception caught.", ex);
                    ex = ex.getCause();
                }
            } finally {
                computer.shutdown();
            }
        }
        
        try {
            if (!computer.getExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warning("Timeout while shutting down.");
            }
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Exception caught.", e);
        }
    }
}
