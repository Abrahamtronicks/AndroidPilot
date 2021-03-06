package com.barbermot.pilot.flight;

import java.util.EnumMap;
import java.util.Map;

import com.barbermot.pilot.quad.QuadCopter;

public class FlightConfiguration {
    
    // Pulse IN
    private final int             ultraSoundPin                   = 4;
    private final int             aileronPinIn                    = 28;
    private final int             rudderPinIn                     = 6;
    private final int             throttlePinIn                   = 7;
    private final int             elevatorPinIn                   = 27;
    private final int             gainPinIn                       = 22;
    
    // PWM
    private final int             aileronPinOut                   = 10;
    private final int             rudderPinOut                    = 11;
    private final int             throttlePinOut                  = 12;
    private final int             elevatorPinOut                  = 13;
    private final int             throttleMonitorPin              = 29;
    private final int             gainPinOut                      = 14;
    
    // UART
    private final int             rxPin                           = 9;
    private final int             txPin                           = 3;
    
    // delay between status messages
    private static final int      MIN_TIME_STATUS_MESSAGE         = 5000;
    
    // values for the PID controller
    private static final float[]  HOVER_CONF                      = { 57f,
            0.001f, 35000f, -600000f, 4000000f                   };
    private static final float[]  LANDING_CONF                    = { 0,
            0.005f, 60000f, -1000000f, 1000000f                  };
    private static final float[]  ORIENTATION_CONF                = { 50f,
            0.07f, 350f, -600f, 400f                             };
    private static final float[]  GPS_CONF                        = { 5.7f,
            0.0007f, 35000f, -4000f, 4000f                       };
    
    // delay between readings of the ultra sound module
    private static final int      MIN_TIME_ULTRA_SOUND            = 100;
    
    // delay between readings of the gyro
    private static final int      MIN_TIME_ORIENTATION            = 150;
    
    private static final long     MIN_TIME_FLIGHT_COMPUTER        = 100;
    
    private static final long     MIN_TIME_RC_ENGAGEMENT          = 250;
    
    // initial min/max throttle setting
    private static final int      MIN_THROTTLE                    = QuadCopter.MIN_SPEED
                                                                          + (QuadCopter.MAX_SPEED - QuadCopter.MIN_SPEED)
                                                                          / 3;
    private static final int      MAX_THROTTLE                    = QuadCopter.MAX_SPEED
                                                                          - (QuadCopter.MAX_SPEED - QuadCopter.MIN_SPEED)
                                                                          / 8;
    
    // min/max for the automatic control of the aileron and elevator
    private static final int      MIN_TILT                        = QuadCopter.MIN_SPEED / 2;
    private static final int      MAX_TILT                        = QuadCopter.MAX_SPEED / 2;
    
    // landings will cut the power once this height is reached
    private static final float    THROTTLE_OFF_HEIGHT             = 0.1f;
    
    // throttle setting for when we don't know the height anymore
    private static final int      EMERGENCY_DESCENT               = QuadCopter.STOP_SPEED
                                                                          - (QuadCopter.MAX_SPEED - QuadCopter.MIN_SPEED)
                                                                          / 20;
    private static final int      EMERGENCY_DESCENT_DELTA         = 20;
    
    private static final int      EMERGENCY_DELTA                 = 1000;
    
    private static final int      EMERGENCY_DELTA_GPS             = 10000;
    
    private static final int      NUM_THREADS                     = 6;
    
    private static final int      MIN_TIME_GPS                    = 100;
    
    private static final float    MIN_SPEED                       = -100;
    private static final float    MAX_SPEED                       = 100;
    
    private static final float    MIN_TILT_ANGLE                  = (float) (-Math.PI / 4f);
    private static final float    MAX_TILT_ANGLE                  = (float) (Math.PI / 4f);
    
    private static final float    MAX_HOVER_HEIGHT                = 3;
    
    private static final float    CALIBRATION_HEIGHT              = 0.05f;
    private static final int      THROTTLE_STEP_FOR_CALIBRATION   = 5;
    private static final long     CALIBRATION_TIME_STEP           = 500;
    private static final long     TIME_BETWEEN_CONNECTION_RETRIES = 1000;
    
    private Map<PinType, Integer> pinMap;
    
    private boolean               isSimulation;
    private ConnectionType        connectionType                  = ConnectionType.TCP;
    private String                serialUrl;
    private int                   serialPort;
    private int                   remoteControlPort;
    private ConnectionType        remoteControlType               = ConnectionType.TCP;
    private int                   defaultGain                     = QuadCopter.STOP_SPEED;
    
    public enum PinType {
        ULTRA_SOUND, AILERON_IN, RUDDER_IN, THROTTLE_IN, ELEVATOR_IN, GAIN_IN, AILERON_OUT, RUDDER_OUT, THROTTLE_OUT, ELEVATOR_OUT, GAIN_OUT, RX, TX, THROTTLE_MONITOR
    };
    
    public enum ConnectionType {
        UART, TCP
    }
    
    private static FlightConfiguration config;
    
    private FlightConfiguration() {
        pinMap = new EnumMap<PinType, Integer>(PinType.class);
        pinMap.put(PinType.ULTRA_SOUND, ultraSoundPin);
        pinMap.put(PinType.AILERON_IN, aileronPinIn);
        pinMap.put(PinType.RUDDER_IN, rudderPinIn);
        pinMap.put(PinType.THROTTLE_IN, throttlePinIn);
        pinMap.put(PinType.ELEVATOR_IN, elevatorPinIn);
        pinMap.put(PinType.GAIN_IN, gainPinIn);
        pinMap.put(PinType.AILERON_OUT, aileronPinOut);
        pinMap.put(PinType.RUDDER_OUT, rudderPinOut);
        pinMap.put(PinType.THROTTLE_OUT, throttlePinOut);
        pinMap.put(PinType.ELEVATOR_OUT, elevatorPinOut);
        pinMap.put(PinType.GAIN_OUT, gainPinOut);
        pinMap.put(PinType.RX, rxPin);
        pinMap.put(PinType.TX, txPin);
        pinMap.put(PinType.THROTTLE_MONITOR, throttleMonitorPin);
    }
    
    public static FlightConfiguration get() {
        return config == null ? (config = new FlightConfiguration()) : config;
    }
    
    public Map<PinType, Integer> getPinMap() {
        return pinMap;
    }
    
    public int getMinTimeStatusMessage() {
        return MIN_TIME_STATUS_MESSAGE;
    }
    
    public float[] getHoverConf() {
        return HOVER_CONF;
    }
    
    public float[] getLandingConf() {
        return LANDING_CONF;
    }
    
    public float[] getOrientationConf() {
        return ORIENTATION_CONF;
    }
    
    public float[] getGpsConf() {
        return GPS_CONF;
    }
    
    public int getMinTimeUltraSound() {
        return MIN_TIME_ULTRA_SOUND;
    }
    
    public int getMinTimeOrientation() {
        return MIN_TIME_ORIENTATION;
    }
    
    public int getMinThrottle() {
        return MIN_THROTTLE;
    }
    
    public int getMaxThrottle() {
        return MAX_THROTTLE;
    }
    
    public int getMinTilt() {
        return MIN_TILT;
    }
    
    public int getMaxTilt() {
        return MAX_TILT;
    }
    
    public float getThrottleOffHeight() {
        return THROTTLE_OFF_HEIGHT;
    }
    
    public int getEmergencyDescent() {
        return EMERGENCY_DESCENT;
    }
    
    public int getEmergencyDescentDelta() {
        return EMERGENCY_DESCENT_DELTA;
    }
    
    public int getEmergencyDelta() {
        return EMERGENCY_DELTA;
    }
    
    public int getEmergencyDeltaGps() {
        return EMERGENCY_DELTA_GPS;
    }
    
    public int getNumberThreads() {
        return NUM_THREADS;
    }
    
    public long getMinTimeFlightComputer() {
        return MIN_TIME_FLIGHT_COMPUTER;
    }
    
    public long getMinTimeRcEngagement() {
        return MIN_TIME_RC_ENGAGEMENT;
    }
    
    public int getMinTimeGps() {
        return MIN_TIME_GPS;
    }
    
    public float getMinSpeed() {
        return MIN_SPEED;
    }
    
    public float getMaxSpeed() {
        return MAX_SPEED;
    }
    
    public float getMaxTiltAngle() {
        return MAX_TILT_ANGLE;
    }
    
    public float getMinTiltAngle() {
        return MIN_TILT_ANGLE;
    }
    
    public float getCalibrationHeight() {
        return CALIBRATION_HEIGHT;
    }
    
    public int getThrottleStepForCalibration() {
        return THROTTLE_STEP_FOR_CALIBRATION;
    }
    
    public float getMaxHoverHeight() {
        return MAX_HOVER_HEIGHT;
    }
    
    public long getCalibrationTimeStep() {
        return CALIBRATION_TIME_STEP;
    }
    
    public void setSimulation(boolean isSimulation) {
        this.isSimulation = isSimulation;
    }
    
    public boolean isSimulation() {
        return isSimulation;
    }
    
    public void setConnectionType(ConnectionType connectionType) {
        this.connectionType = connectionType;
    }
    
    public ConnectionType getConnectionType() {
        return connectionType;
    }
    
    public void setSerialUrl(String serialUrl) {
        this.serialUrl = serialUrl;
    }
    
    public String getSerialUrl() {
        return serialUrl;
    }
    
    public void setSerialPort(int serialPort) {
        this.serialPort = serialPort;
    }
    
    public int getSerialPort() {
        return serialPort;
    }
    
    public long getWaitBetweenConnectionRetries() {
        return TIME_BETWEEN_CONNECTION_RETRIES;
    }
    
    public void setRemoteControlPort(int port) {
        remoteControlPort = port;
    }
    
    public int getRemoteControlPort() {
        return remoteControlPort;
    }
    
    public ConnectionType getRemoteControlType() {
        return remoteControlType;
    }
    
    public int getDefaultGain() {
        return defaultGain;
    }
    
    public void setDefaultGain(int gain) {
        defaultGain = gain;
    }
}
