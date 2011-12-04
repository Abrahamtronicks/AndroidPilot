package com.barbermot.pilot.flight.state;

import ioio.lib.api.exception.ConnectionLostException;

import com.barbermot.pilot.flight.Waypoint;

public class WaypointHoldState extends FlightState<Waypoint> {
    
    @Override
    public void enter(Waypoint arg) throws ConnectionLostException {
        logger.info("Entering waypoint hold state");
    }
    
    @Override
    public void exit() throws ConnectionLostException {}
    
    @Override
    public void update() throws ConnectionLostException {}
    
}
