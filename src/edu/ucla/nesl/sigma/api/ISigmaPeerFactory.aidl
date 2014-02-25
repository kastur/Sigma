package edu.ucla.nesl.sigma.api;

import edu.ucla.nesl.sigma.api.ISigmaPeer;
import edu.ucla.nesl.sigma.api.IRequestHandler;

interface ISigmaPeerFactory {
    ISigmaPeer newInstance(in byte[] /* Wire URI */ uriBytes, IRequestHandler requestHandler, in Bundle extras);
    void destroyInstance(in byte[] /* Wire URI */ uriBytes);
}
