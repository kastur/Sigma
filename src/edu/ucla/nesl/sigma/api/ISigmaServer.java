package edu.ucla.nesl.sigma.api;

import edu.ucla.nesl.sigma.base.SigmaEngine;

public interface ISigmaServer {
    public SigmaEngine getEngine();

    public void start();

    public void stop();
}
