package edu.ucla.nesl.sigma.api;

interface IRequestSender {
    byte[] /* Wire SResponse */ send(in byte[] /* Wire SRequest */ requestBytes);
}
