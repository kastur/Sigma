package edu.ucla.nesl.sigma.api;

import edu.ucla.nesl.sigma.api.IRequestHandler;
import edu.ucla.nesl.sigma.api.IRequestSender;

interface ISigmaPeer {
  byte[] /* Wire SResponse */ send(in byte[] /* Wire SRequest */ requestBytes);
}
