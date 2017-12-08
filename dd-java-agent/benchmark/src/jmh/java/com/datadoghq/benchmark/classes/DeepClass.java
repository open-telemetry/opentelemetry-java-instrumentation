package com.datadoghq.benchmark.classes;

import com.datadoghq.trace.Trace;

public class DeepClass implements C {

  @Override
  public void interfaceTrace() {
  }

  @Override
  public void something() {
  }

  @Trace
  @Override
  public void somethingElse() {
  }
}
