package config.exclude.packagename;

import io.opentracing.contrib.dropwizard.Trace;

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
