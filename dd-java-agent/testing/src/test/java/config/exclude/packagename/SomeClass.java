package config.exclude.packagename;

import datadog.trace.api.Trace;

public class SomeClass implements Runnable {

  @Trace
  @Override
  public void run() {}

  public static class NestedClass implements Runnable {

    @Trace
    @Override
    public void run() {}
  }
}
