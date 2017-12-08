package com.datadoghq.benchmark.classes;

import com.datadoghq.trace.Trace;

public class SimpleClass {
  @Trace
  public void aMethodToTrace() {
  }
}
