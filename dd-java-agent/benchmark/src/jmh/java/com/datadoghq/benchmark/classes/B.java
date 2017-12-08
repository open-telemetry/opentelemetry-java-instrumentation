package com.datadoghq.benchmark.classes;

import com.datadoghq.trace.Trace;

public interface B extends A {
  @Trace
  void something();
}
