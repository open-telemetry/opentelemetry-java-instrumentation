package com.datadoghq.benchmark.classes;

import com.datadoghq.trace.Trace;

public interface A {
  @Trace
  void interfaceTrace();
}
