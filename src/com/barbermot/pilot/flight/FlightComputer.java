package com.barbermot.pilot.flight;

import ioio.lib.api.exception.ConnectionLostException;

import java.io.PrintStream;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import com.barbermot.pilot.flight.state.FlightState;
import com.barbermot.pilot.flight.state.StateEvent;
import com.barbermot.pilot.pid.AutoControl;
import com.barbermot.pilot.quad.QuadCopter;
import com.barbermot.pilot.rc.RemoteControl;

public class FlightComputer implements Runnable {
    
    private static final Logger      logger = Logger.getLogger("FlightComputer");
    
    private FlightState<?>           state;
    
    private FlightConfiguration      config;
    
    // the actual QuadCopter
    private QuadCopter               ufo;
    
    // RC signals (from RC controller)
    private RemoteControl            rc;
    
    // autopilot for throttle
    private AutoControl              autoThrottle;
    
    // autopilot for elevator
    private AutoControl              autoElevator;
    
    // autopilot for aileron
    private AutoControl              autoAileron;
    
    // autopilot for rudder
    private AutoControl              autoRudder;
    
    // values for the PID controller
    private float[]                  hoverConf;
    private float[]                  landingConf;
    private float[]                  orientationConf;
    
    // Log writer
    private PrintStream              printer;
    
    // min/max for the automatic control of the throttle
    private int                      minThrottle;
    private int                      maxThrottle;
    
    private int                      minTilt;
    private int                      maxTilt;
    
    private float                    minTiltAngle;
    private float                    maxTiltAngle;
    
    private float                    minSpeed;
    private float                    maxSpeed;
    
    private float                    height;
    private float                    zeroHeight;
    
    private float                    gpsHeight;
    private float                    zeroGpsHeight;
    
    private float                    goalHeight;
    
    private float                    longitudinalDisplacement;
    private float                    lateralDisplacement;
    private float                    heading;
    
    private volatile long            time;
    private volatile long            lastTimeOrientationSignal;
    private volatile long            lastTimeHeightSignal;
    private volatile long            lastTimeGpsHeight;
    
    private int                      currentThrottle;
    private int                      currentElevator;
    private int                      currentAileron;
    private int                      currentRudder;
    
    private ScheduledExecutorService scheduler;
    
    public FlightComputer() {
        config = FlightConfiguration.get();
        
        this.hoverConf = config.getHoverConf();
        this.landingConf = config.getLandingConf();
        this.orientationConf = config.getOrientationConf();
        
        this.minThrottle = config.getMinThrottle();
        this.maxThrottle = config.getMaxThrottle();
        
        this.minTilt = config.getMinTilt();
        this.maxTilt = config.getMaxTilt();
        
        this.minSpeed = config.getMinSpeed();
        this.maxSpeed = config.getMinSpeed();
        
        this.minTiltAngle = config.getMinTiltAngle();
        this.maxTiltAngle = config.getMaxTiltAngle();
        
        time = System.currentTimeMillis();
        lastTimeHeightSignal = time;
        lastTimeOrientationSignal = time;
    }
    
    public void shutdown() {
        scheduler.shutdownNow();
    }
    
    public synchronized void takeoff(float height)
            throws ConnectionLostException {
        state.transition(new StateEvent<Float>(FlightState.Type.HOVER, height));
    }
    
    public synchronized void hover(float height) throws ConnectionLostException {
        state.transition(new StateEvent<Float>(FlightState.Type.HOVER, height));
    }
    
    public synchronized void ground() throws ConnectionLostException {
        state.transition(new StateEvent<Void>(FlightState.Type.GROUND, null));
    }
    
    public synchronized void land() throws ConnectionLostException {
        state.transition(new StateEvent<Float>(FlightState.Type.LANDING, null));
    }
    
    public synchronized void emergencyDescent() throws ConnectionLostException {
        state.transition(new StateEvent<Float>(
                FlightState.Type.EMERGENCY_LANDING, height));
    }
    
    public synchronized void manualControl() throws ConnectionLostException {
        state.transition(new StateEvent<Float>(FlightState.Type.MANUAL_CONTROL,
                null));
    }
    
    public synchronized void autoControl() throws ConnectionLostException {
        state.transition(new StateEvent<Float>(FlightState.Type.HOVER, height));
    }
    
    public synchronized void abort() throws ConnectionLostException {
        state.transition(new StateEvent<Float>(FlightState.Type.FAILED, null));
    }
    
    public synchronized void stabilize(boolean b)
            throws ConnectionLostException {
        if (b) {
            state.transition(new StateEvent<Float>(
                    FlightState.Type.STABILIZED_HOVER, goalHeight));
        } else {
            state.transition(new StateEvent<Float>(FlightState.Type.HOVER,
                    goalHeight));
        }
    }
    
    public synchronized void forward(int speed) {
        if (state.getType() == FlightState.Type.STABILIZED_HOVER) {
            float angle = map(speed, minSpeed, maxSpeed, minTiltAngle,
                    maxTiltAngle);
            autoElevator.setGoal(angle);
        }
    }
    
    public synchronized void sideways(int speed) {
        if (state.getType() == FlightState.Type.STABILIZED_HOVER) {
            float angle = map(speed, minSpeed, maxSpeed, minTiltAngle,
                    maxTiltAngle);
            autoElevator.setGoal(angle);
        }
    }
    
    public synchronized void rotate(int angle) {
        if (state.getType() == FlightState.Type.STABILIZED_HOVER) {
            float radian = map(angle, -180, 180, (float) -Math.PI,
                    (float) Math.PI);
            autoRudder.setGoal(radian);
        }
    }
    
    private float map(float value, float minIn, float maxIn, float minOut,
            float maxOut) {
        return ((value - minIn) / (maxIn - minIn)) * (maxOut - minOut) + minOut;
    }
    
    public synchronized void run() {
        try {
            time = System.currentTimeMillis();
            
            // the following state transitions can origin in any state
            
            // allow for manual inputs first
            if (rc.getControlMask() == RemoteControl.FULL_MANUAL) {
                logger.info("Manual control is engaged");
                manualControl();
            }
            
            // no height signal from ultra sound try descending
            if (!hasHeightSignal()) {
                
                logger.warning("Last height: " + lastTimeHeightSignal);
                emergencyDescent();
            }
            
            state.update();
        } catch (ConnectionLostException e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean hasHeightSignal() {
        return (time - lastTimeHeightSignal) < config.getEmergencyDelta();
    }
    
    public float[] getHoverConf() {
        return hoverConf;
    }
    
    public void setHoverConf(float[] hoverConf) {
        this.hoverConf = hoverConf;
    }
    
    public float[] getLandingConf() {
        return landingConf;
    }
    
    public void setLandingConf(float[] landingConf) {
        this.landingConf = landingConf;
    }
    
    public float[] getOrientationConf() {
        return orientationConf;
    }
    
    public void setOrientationConf(float[] orientationConf) {
        this.orientationConf = orientationConf;
    }
    
    public void setHoverConfiguration(float[] conf) {
        hoverConf = conf;
    }
    
    public void setLandingConfiguration(float[] conf) {
        landingConf = conf;
    }
    
    public void setStabilizerConfiguration(float[] conf) {
        orientationConf = conf;
    }
    
    public void setMinThrottle(int min) {
        this.minThrottle = min;
    }
    
    public void setMaxThrottle(int max) {
        this.maxThrottle = max;
    }
    
    public float getLongitudinalDisplacement() {
        return longitudinalDisplacement;
    }
    
    public void setLongitudinalDisplacement(float longitudinalDisplacement) {
        this.longitudinalDisplacement = longitudinalDisplacement;
    }
    
    public float getLateralDisplacement() {
        return lateralDisplacement;
    }
    
    public void setLateralDisplacement(float lateralDisplacement) {
        this.lateralDisplacement = lateralDisplacement;
    }
    
    public QuadCopter getUfo() {
        return ufo;
    }
    
    public void setUfo(QuadCopter ufo) {
        this.ufo = ufo;
    }
    
    public AutoControl getAutoThrottle() {
        return autoThrottle;
    }
    
    public void setAutoThrottle(AutoControl autoThrottle) {
        this.autoThrottle = autoThrottle;
    }
    
    public AutoControl getAutoElevator() {
        return autoElevator;
    }
    
    public void setAutoElevator(AutoControl autoElevator) {
        this.autoElevator = autoElevator;
    }
    
    public AutoControl getAutoAileron() {
        return autoAileron;
    }
    
    public void setAutoAileron(AutoControl autoAileron) {
        this.autoAileron = autoAileron;
    }
    
    public AutoControl getAutoRudder() {
        return autoRudder;
    }
    
    public void setAutoRudder(AutoControl autoRudder) {
        this.autoRudder = autoRudder;
    }
    
    public PrintStream getPrinter() {
        return printer;
    }
    
    public void setPrinter(PrintStream printer) {
        this.printer = printer;
    }
    
    public int getMinThrottle() {
        return minThrottle;
    }
    
    public int getMaxThrottle() {
        return maxThrottle;
    }
    
    public void setMinTilt(int minTilt) {
        this.minTilt = minTilt;
    }
    
    public int getMinTilt() {
        return minTilt;
    }
    
    public void setMaxTilt(int maxTilt) {
        this.maxTilt = maxTilt;
    }
    
    public int getMaxTilt() {
        return maxTilt;
    }
    
    public long getLastTimeOrientationSignal() {
        return lastTimeOrientationSignal;
    }
    
    public void setLastTimeOrientationSignal(long lastTimeOrientationSignal) {
        this.lastTimeOrientationSignal = lastTimeOrientationSignal;
    }
    
    public long getLastTimeHeightSignal() {
        return lastTimeHeightSignal;
    }
    
    public void setLastTimeHeightSignal(long lastTimeHeightSignal) {
        this.lastTimeHeightSignal = lastTimeHeightSignal;
    }
    
    public void setExecutor(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
    
    public ScheduledExecutorService getExecutor() {
        return scheduler;
    }
    
    public RemoteControl getRc() {
        return rc;
    }
    
    public void setRc(RemoteControl rc) {
        this.rc = rc;
    }
    
    public FlightState<?> getState() {
        return state;
    }
    
    public void setState(FlightState<?> state) {
        this.state = state;
    }
    
    public float getHeight() {
        return height;
    }
    
    public void setHeight(float height) {
        this.height = height;
    }
    
    public float getHeading() {
        return heading;
    }
    
    public void setHeading(float heading) {
        this.heading = heading;
    }
    
    public long getTime() {
        return time;
    }
    
    public void setTime(long time) {
        this.time = time;
    }
    
    public int getCurrentThrottle() {
        return currentThrottle;
    }
    
    public void setCurrentThrottle(int currentThrottle) {
        this.currentThrottle = currentThrottle;
    }
    
    public int getCurrentElevator() {
        return currentElevator;
    }
    
    public void setCurrentElevator(int currentElevator) {
        this.currentElevator = currentElevator;
    }
    
    public int getCurrentAileron() {
        return currentAileron;
    }
    
    public void setCurrentAileron(int currentAileron) {
        this.currentAileron = currentAileron;
    }
    
    public int getCurrentRudder() {
        return currentRudder;
    }
    
    public void setCurrentRudder(int currentRudder) {
        this.currentRudder = currentRudder;
    }
    
    public void setGpsHeight(float gpsHeight) {
        this.gpsHeight = gpsHeight;
    }
    
    public float getGpsHeight() {
        return gpsHeight;
    }
    
    public long getLastTimeGpsHeight() {
        return lastTimeGpsHeight;
    }
    
    public void setLastTimeGpsHeight(long lastTimeGpsHeight) {
        this.lastTimeGpsHeight = lastTimeGpsHeight;
    }
    
    public float getZeroHeight() {
        return zeroHeight;
    }
    
    public void setZeroHeight(float zeroHeight) {
        this.zeroHeight = zeroHeight;
    }
    
    public float getZeroGpsHeight() {
        return zeroGpsHeight;
    }
    
    public void setZeroGpsHeight(float zeroGpsHeight) {
        this.zeroGpsHeight = zeroGpsHeight;
    }
    
    public void setGoalHeight(float height) {
        this.goalHeight = height;
    }
}