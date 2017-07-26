package com.datadoghq.trace.util;

public class Clock {

  public synchronized static long nowNanos() {
    return System.nanoTime();
  }
}
