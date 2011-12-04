package com.barbermot.pilot.simulator;

import ioio.lib.api.IOIO;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.barbermot.pilot.builder.BuildException;
import com.barbermot.pilot.builder.FlightBuilder;
import com.barbermot.pilot.flight.FlightComputer;
import com.barbermot.pilot.signal.SignalManagerFactory;

public class Simulation {
    
    public static void main(String[] args) {
        PhysicsEngine engine = new PhysicsEngine();
        IOIO ioio = new IOIOSimulation(engine);
        FlightBuilder builder = new FlightBuilder();
        FlightComputer computer = null;
        SignalManagerFactory.setManager(new SignalManagerSimulation(engine,
                ioio));
        
        try {
            computer = builder.getComputer(ioio, null, null);
        } catch (BuildException e) {
            e.printStackTrace();
        }
        
        for (Future<?> f : builder.getFutures()) {
            try {
                f.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                Throwable ex = e;
                while (ex != null) {
                    ex.printStackTrace();
                    ex = ex.getCause();
                }
            } finally {
                computer.shutdown();
            }
        }
        
        try {
            if (!computer.getExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                System.out.println("Shutdown failed.");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
