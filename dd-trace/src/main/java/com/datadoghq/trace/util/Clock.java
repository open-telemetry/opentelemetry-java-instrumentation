package com.datadoghq.trace.util;

import java.util.concurrent.TimeUnit;

public class Clock {

  public static synchronized long currentNanoTicks() {
    return System.nanoTime();
  }

  public static synchronized long currentMicroTime() {
    return TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
  }
}
