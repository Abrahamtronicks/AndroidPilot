package com.barbermot.pilot.builder;

import static com.barbermot.pilot.flight.FlightConfiguration.PinType.AILERON_IN;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.AILERON_OUT;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.ELEVATOR_IN;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.ELEVATOR_OUT;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.GAIN_IN;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.GAIN_OUT;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.RUDDER_IN;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.RUDDER_OUT;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.RX;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.THROTTLE_IN;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.THROTTLE_MONITOR;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.THROTTLE_OUT;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.TX;
import static com.barbermot.pilot.flight.FlightConfiguration.PinType.ULTRA_SOUND;
import static com.barbermot.pilot.flight.state.FlightState.Type.CALIBRATION;
import static com.barbermot.pilot.flight.state.FlightState.Type.EMERGENCY_LANDING;
import static com.barbermot.pilot.flight.state.FlightState.Type.FAILED;
import static com.barbermot.pilot.flight.state.FlightState.Type.GROUND;
import static com.barbermot.pilot.flight.state.FlightState.Type.HOVER;
import static com.barbermot.pilot.flight.state.FlightState.Type.LANDING;
import static com.barbermot.pilot.flight.state.FlightState.Type.MANUAL_CONTROL;
import static com.barbermot.pilot.flight.state.FlightState.Type.STABILIZED_HOVER;
import static com.barbermot.pilot.flight.state.FlightState.Type.WAYPOINT_HOLD;
import static com.barbermot.pilot.flight.state.FlightState.Type.WAYPOINT_TRACK;
import ioio.lib.api.IOIO;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import android.hardware.SensorManager;
import android.location.LocationManager;

import com.barbermot.pilot.flight.AileronControlListener;
import com.barbermot.pilot.flight.ElevatorControlListener;
import com.barbermot.pilot.flight.FlightComputer;
import com.barbermot.pilot.flight.FlightConfiguration;
import com.barbermot.pilot.flight.FlightControlListener;
import com.barbermot.pilot.flight.RudderControlListener;
import com.barbermot.pilot.flight.ThrottleControlListener;
import com.barbermot.pilot.flight.state.CalibrationState;
import com.barbermot.pilot.flight.state.EmergencyLandingState;
import com.barbermot.pilot.flight.state.FailedState;
import com.barbermot.pilot.flight.state.FlightState;
import com.barbermot.pilot.flight.state.GroundState;
import com.barbermot.pilot.flight.state.HoverState;
import com.barbermot.pilot.flight.state.LandingState;
import com.barbermot.pilot.flight.state.ManualControlState;
import com.barbermot.pilot.flight.state.StabilizedHoverState;
import com.barbermot.pilot.flight.state.WaypointHoldState;
import com.barbermot.pilot.flight.state.WaypointTrackState;
import com.barbermot.pilot.logger.FlightLogger;
import com.barbermot.pilot.parser.SerialController;
import com.barbermot.pilot.pid.AutoControl;
import com.barbermot.pilot.pid.GpsAutoControl;
import com.barbermot.pilot.pid.RadianAutoControl;
import com.barbermot.pilot.quad.QuadCopter;
import com.barbermot.pilot.rc.RemoteControl;
import com.barbermot.pilot.signal.Signal;
import com.barbermot.pilot.signal.SignalListener;
import com.barbermot.pilot.signal.SignalManager;
import com.barbermot.pilot.signal.SignalManagerFactory;

/**
 * FlightBuilder wires up the system. It builds the flight computer instance and
 * hooks it up with the respective signals and auto controls. FlightBuilder also
 * starts the processing.
 * 
 * It is a one time instance. It can only be used to reliably create one
 * instance of the system and when a disconnect from ioio occurs the system has
 * to be shutdown and a new instance is needed to resume operation after the
 * connection comes back.
 */
public class FlightBuilder {
    
    private static final Logger                       logger = Logger.getLogger("FlightBuilder");
    
    private FlightComputer                            computer;
    
    private PrintStream                               printer;
    
    private SensorManager                             sensorManager;
    private SignalManager                             signalManager;
    private LocationManager                           locationManager;
    
    private ScheduledExecutorService                  scheduler;
    
    private QuadCopter                                ufo;
    
    private FlightConfiguration                       config;
    
    private Map<FlightConfiguration.PinType, Integer> map;
    
    private IOIO                                      ioio;
    private Uart                                      uart;
    
    private AutoControl                               autoThrottle;
    private AutoControl                               autoAileron;
    private AutoControl                               autoElevator;
    private AutoControl                               autoRudder;
    
    private AutoControl                               autoGpsThrottle;
    private AutoControl                               autoGpsAileron;
    private AutoControl                               autoGpsElevator;
    
    private EnumMap<FlightState.Type, FlightState<?>> stateMap;
    private List<Future<?>>                           futures;
    
    /**
     * getComputer builds the FlightComputer. It hooks up the controls to the
     * respective signals and starts processing.
     * 
     * @param ioio
     *            A valid connection to the IOIO board.
     * @param manager
     *            An Android SensorManager to access the phone sensors
     * @return A one time instance of the FlightComputer
     * @throws BuildException
     */
    public FlightComputer getComputer(IOIO ioio, SensorManager sensorManager,
            LocationManager locationManager) throws BuildException {
        try {
            futures = new LinkedList<Future<?>>();
            this.sensorManager = sensorManager;
            this.locationManager = locationManager;
            this.computer = new FlightComputer();
            this.config = FlightConfiguration.get();
            this.map = config.getPinMap();
            this.stateMap = new EnumMap<FlightState.Type, FlightState<?>>(
                    FlightState.Type.class);
            this.ioio = ioio;
            
            buildScheduler();
            buildUart();
            buildPrinter();
            buildQuadCopter();
            buildRemoteControl();
            buildControls();
            buildFlightStates();
            buildTransitions();
            buildSignalArray();
            buildLogger();
            buildSerialController();
            
            futures.add(scheduler.scheduleWithFixedDelay(computer, 0,
                    config.getMinTimeFlightComputer(), TimeUnit.MILLISECONDS));
        } catch (ConnectionLostException e) {
            e.printStackTrace();
            throw new BuildException();
        }
        return computer;
    }
    
    /**
     * getFutures returns handles to the periodic and continuous tasks started
     * by the FlightBuilder. These futures can be used to orderly shutdown the
     * system.
     * 
     * @return A list of futures for all the tasks started by the builder.
     */
    public List<Future<?>> getFutures() {
        return futures;
    }
    
    public SignalManager getSignalManager() {
        return signalManager;
    }
    
    private void buildScheduler() {
        logger.info("Setting up scheduler");
        
        scheduler = new ScheduledThreadPoolExecutor(config.getNumberThreads());
        computer.setExecutor(scheduler);
    }
    
    private void buildUart() throws ConnectionLostException {
        logger.info("Setting up UART");
        
        uart = ioio.openUart(config.getPinMap().get(RX), config.getPinMap()
                .get(TX), 9600, Uart.Parity.NONE, Uart.StopBits.ONE);
    }
    
    private void buildLogger() {
        logger.info("Setting up logger");
        
        FlightLogger logger = new FlightLogger(printer);
        logger.setComputer(computer);
        futures.add(scheduler.scheduleWithFixedDelay(logger, 0,
                config.getMinTimeStatusMessage(), TimeUnit.MILLISECONDS));
    }
    
    private void buildSerialController() throws ConnectionLostException {
        logger.info("Setting up serial controller");
        
        InputStream in = uart.getInputStream();
        SerialController controller = new SerialController(computer, ';', in,
                printer);
        futures.add(scheduler.submit(controller));
    }
    
    private void buildControls() {
        logger.info("Setting up controls");
        
        FlightControlListener listener;
        
        listener = new ThrottleControlListener();
        listener.setComputer(computer);
        autoThrottle = new AutoControl(listener,
                Logger.getLogger("ThrottleControl"));
        autoGpsThrottle = new AutoControl(listener,
                Logger.getLogger("ThrottleGpsControl"));
        
        listener = new AileronControlListener();
        listener.setComputer(computer);
        autoAileron = new RadianAutoControl(listener,
                Logger.getLogger("AileronControl"));
        autoGpsAileron = new GpsAutoControl(listener,
                Logger.getLogger("AileronGpsControl"));
        
        listener = new RudderControlListener();
        listener.setComputer(computer);
        autoRudder = new RadianAutoControl(listener,
                Logger.getLogger("RudderControl"));
        
        listener = new ElevatorControlListener();
        listener.setComputer(computer);
        autoElevator = new RadianAutoControl(listener,
                Logger.getLogger("ElevatorControl"));
        autoGpsElevator = new GpsAutoControl(listener,
                Logger.getLogger("ElevatorGpsControl"));
        
    }
    
    private void buildQuadCopter() throws ConnectionLostException {
        logger.info("Setting up Quadcopter");
        
        ufo = new QuadCopter(ioio, map.get(AILERON_OUT), map.get(RUDDER_OUT),
                map.get(THROTTLE_OUT), map.get(ELEVATOR_OUT), map.get(GAIN_OUT));
        computer.setUfo(ufo);
    }
    
    private void buildRemoteControl() throws ConnectionLostException {
        logger.info("Setting up remote control");
        
        RemoteControl rc = new RemoteControl(ioio, ufo, map.get(AILERON_IN),
                map.get(RUDDER_IN), map.get(THROTTLE_IN), map.get(ELEVATOR_IN),
                map.get(THROTTLE_MONITOR), map.get(GAIN_IN));
        
        rc.setControlMask((char) ~RemoteControl.THROTTLE_MASK);
        computer.setRc(rc);
        
        futures.add(scheduler.scheduleWithFixedDelay(rc, 0,
                config.getMinTimeRcEngagement(), TimeUnit.MILLISECONDS));
    }
    
    private void buildPrinter() throws ConnectionLostException {
        logger.info("Setting up printer");
        
        printer = new PrintStream(uart.getOutputStream());
    }
    
    private void buildFlightStates() throws ConnectionLostException {
        logger.info("Setting up state machine");
        
        FlightState.Type type = EMERGENCY_LANDING;
        FlightState<?> state = new EmergencyLandingState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        
        type = FAILED;
        state = new FailedState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        
        type = GROUND;
        state = new GroundState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        computer.setState(state);
        
        type = CALIBRATION;
        state = new CalibrationState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        
        type = HOVER;
        state = new HoverState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        ((HoverState) state).setAutoThrottle(autoThrottle);
        
        type = LANDING;
        state = new LandingState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        ((LandingState) state).setAutoThrottle(autoThrottle);
        
        type = MANUAL_CONTROL;
        state = new ManualControlState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        
        type = STABILIZED_HOVER;
        state = new StabilizedHoverState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        ((StabilizedHoverState) state).setAutoThrottle(autoThrottle);
        ((StabilizedHoverState) state).setAutoElevator(autoElevator);
        ((StabilizedHoverState) state).setAutoAileron(autoAileron);
        ((StabilizedHoverState) state).setAutoRudder(autoRudder);
        
        type = WAYPOINT_HOLD;
        state = new WaypointHoldState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
        ((WaypointHoldState) state).setAutoUltraSoundThrottle(autoThrottle);
        ((WaypointHoldState) state).setAutoGpsThrottle(autoGpsThrottle);
        ((WaypointHoldState) state).setAutoAileron(autoGpsAileron);
        ((WaypointHoldState) state).setAutoElevator(autoGpsElevator);
        ((WaypointHoldState) state).setAutoRudder(autoRudder);
        
        type = WAYPOINT_TRACK;
        state = new WaypointTrackState();
        state.setType(type);
        state.setComputer(computer);
        stateMap.put(type, state);
    }
    
    private void buildTransitions() throws ConnectionLostException {
        logger.info("Setting up transitions");
        
        FlightState<?> abort = stateMap.get(FAILED);
        for (FlightState<?> state : stateMap.values()) {
            if (state != abort) {
                state.addTransition(abort);
            }
        }
        
        FlightState<?> manual = stateMap.get(MANUAL_CONTROL);
        for (FlightState<?> state : stateMap.values()) {
            if (state != manual) {
                state.addTransition(manual);
            }
        }
        
        // Ground
        stateMap.get(GROUND).addTransition(stateMap.get(HOVER));
        stateMap.get(GROUND).addTransition(stateMap.get(WAYPOINT_HOLD));
        stateMap.get(GROUND).addTransition(stateMap.get(CALIBRATION));
        
        // Calibration
        stateMap.get(CALIBRATION)
                .addTransition(stateMap.get(EMERGENCY_LANDING));
        stateMap.get(CALIBRATION).addTransition(stateMap.get(LANDING));
        
        // Hover
        stateMap.get(HOVER).addTransition(stateMap.get(HOVER));
        stateMap.get(HOVER).addTransition(stateMap.get(STABILIZED_HOVER));
        stateMap.get(HOVER).addTransition(stateMap.get(WAYPOINT_HOLD));
        stateMap.get(HOVER).addTransition(stateMap.get(EMERGENCY_LANDING));
        stateMap.get(HOVER).addTransition(stateMap.get(LANDING));
        
        // Emergency Landing
        stateMap.get(EMERGENCY_LANDING).addTransition(stateMap.get(LANDING));
        
        // Landing
        stateMap.get(LANDING).addTransition(stateMap.get(HOVER));
        stateMap.get(LANDING).addTransition(stateMap.get(WAYPOINT_HOLD));
        stateMap.get(LANDING).addTransition(stateMap.get(EMERGENCY_LANDING));
        stateMap.get(LANDING).addTransition(stateMap.get(GROUND));
        
        // Stabilized Hover
        stateMap.get(STABILIZED_HOVER).addTransition(stateMap.get(HOVER));
        stateMap.get(STABILIZED_HOVER).addTransition(
                stateMap.get(WAYPOINT_HOLD));
        stateMap.get(STABILIZED_HOVER).addTransition(
                stateMap.get(STABILIZED_HOVER));
        stateMap.get(STABILIZED_HOVER).addTransition(stateMap.get(LANDING));
        stateMap.get(STABILIZED_HOVER).addTransition(
                stateMap.get(EMERGENCY_LANDING));
        
        // Waypoint hold
        stateMap.get(WAYPOINT_HOLD).addTransition(stateMap.get(HOVER));
        stateMap.get(WAYPOINT_HOLD).addTransition(
                stateMap.get(STABILIZED_HOVER));
        stateMap.get(WAYPOINT_HOLD).addTransition(stateMap.get(WAYPOINT_HOLD));
        stateMap.get(WAYPOINT_HOLD).addTransition(
                stateMap.get(EMERGENCY_LANDING));
        stateMap.get(WAYPOINT_HOLD).addTransition(stateMap.get(LANDING));
        
        // Manual Control
        stateMap.get(MANUAL_CONTROL).addTransition(stateMap.get(HOVER));
        stateMap.get(MANUAL_CONTROL).addTransition(stateMap.get(LANDING));
        
    }
    
    private void buildSignalArray() throws ConnectionLostException {
        logger.info("Setting up signal array");
        
        signalManager = SignalManagerFactory.getManager(ioio, sensorManager,
                locationManager, scheduler);
        Signal signal = signalManager.getUltraSoundSignal(
                config.getMinTimeUltraSound(), map.get(ULTRA_SOUND));
        
        signal.registerListener(new SignalListener() {
            
            public void update(float x, long time) {
                computer.setHeight(x);
                computer.setLastTimeHeightSignal(time);
            }
        });
        signal.registerListener(autoThrottle);
        
        signal = signalManager.getRollSignal(config.getMinTimeOrientation());
        signal.registerListener(new SignalListener() {
            
            public void update(float x, long time) {
                computer.setLateralDisplacement(x);
                computer.setLastTimeOrientationSignal(time);
            }
        });
        signal.registerListener(autoAileron);
        
        signal = signalManager.getPitchSignal(config.getMinTimeOrientation());
        signal.registerListener(new SignalListener() {
            
            public void update(float x, long time) {
                computer.setLongitudinalDisplacement(x);
                computer.setLastTimeOrientationSignal(time);
            }
        });
        signal.registerListener(autoElevator);
        
        signal = signalManager.getYawSignal(config.getMinTimeOrientation());
        signal.registerListener(new SignalListener() {
            
            public void update(float x, long time) {
                computer.setHeading(x);
                computer.setLastTimeOrientationSignal(time);
            }
        });
        signal.registerListener(autoRudder);
        
        signal = signalManager.getGpsAltitudeSignal(config.getMinTimeGps());
        signal.registerListener(new SignalListener() {
            
            public void update(float x, long time) {
                computer.setGpsHeight(x);
                computer.setLastTimeGpsHeight(time);
            }
        });
        signal.registerListener(autoGpsThrottle);
        
        signal = signalManager.getGpsLatitudeSignal(config.getMinTimeGps());
        signal.registerListener(new SignalListener() {
            
            public void update(float x, long time) {
                computer.setLatitude(x);
            }
        });
        signal.registerListener(autoGpsElevator);
        
        signal = signalManager.getGpsLongitudeSignal(config.getMinTimeGps());
        signal.registerListener(new SignalListener() {
            
            public void update(float x, long time) {
                computer.setLongitude(x);
            }
        });
        signal.registerListener(autoGpsAileron);
        
        futures.addAll(signalManager.getFutures());
    }
}
