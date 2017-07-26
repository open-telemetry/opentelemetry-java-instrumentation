package com.datadoghq.trace.util;

public class Clock {

  public static synchronized long nowNanos() {
    return System.nanoTime();
  }
}
