package com.barbermot.pilot.flight.state;

import ioio.lib.api.exception.ConnectionLostException;

import com.barbermot.pilot.flight.FlightConfiguration;
import com.barbermot.pilot.quad.QuadCopter;

public class CalibrationState extends FlightState<Void> {
    
    int        currentThrottle = -100;
    QuadCopter ufo;
    long       lastAdjustmentMillis;
    
    @Override
    public boolean guard(Void arg) throws ConnectionLostException {
        return true;
    }
    
    @Override
    public void enter(Void arg) throws ConnectionLostException {
        ufo = computer.getUfo();
        ufo.throttle(currentThrottle);
        computer.setCurrentThrottle(currentThrottle);
    }
    
    @Override
    public void exit() throws ConnectionLostException {}
    
    @Override
    public void update() throws ConnectionLostException {
        if (!computer.hasHeightSignal()) {
            transition(new StateEvent<Void>(Type.EMERGENCY_LANDING, null));
        }
        
        if ((computer.getHeight() - computer.getZeroHeight()) > FlightConfiguration
                .get().getCalibrationHeight()) {
            computer.setZeroThrottle(currentThrottle
                    - FlightConfiguration.get().getThrottleStepForCalibration());
            transition(new StateEvent<Void>(Type.LANDING, null));
        } else {
            long millis = System.currentTimeMillis();
            if ((millis - lastAdjustmentMillis) > FlightConfiguration.get()
                    .getCalibrationTimeStep()) {
                currentThrottle += FlightConfiguration.get()
                        .getThrottleStepForCalibration();
                ufo = computer.getUfo();
                ufo.throttle(currentThrottle);
                computer.setCurrentThrottle(currentThrottle);
                lastAdjustmentMillis = millis;
            }
        }
    }
}
