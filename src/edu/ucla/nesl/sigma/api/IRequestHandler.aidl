package edu.ucla.nesl.sigma.api;

interface IRequestHandler {
    /* Wire SReponse */ byte[] handleRequest(in byte[] /* Wire SRequest */ requestBytes);
}
