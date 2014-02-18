package edu.ucla.nesl.sigma.samples.pingpong;

interface IPingPongServer {
  void ping(IPingPongServer other, int count);
  void pong(IPingPongServer other, int count);

  void putObject(IPingPongServer obj);
  int getRandom();
}