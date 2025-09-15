package io.opentelemetry.smoketest;

@FunctionalInterface
public interface TargetRunner {
  void runInTarget() throws Exception;
}
