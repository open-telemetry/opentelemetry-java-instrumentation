package io.opentelemetry.benchmark.storage;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;

@Fork(
    jvmArgsAppend = {
        "-javaagent:/Users/nsalnikovtarnovski/Documents/workspace/opentelemetry-auto-instr-java/opentelemetry-javaagent/build/libs/opentelemetry-javaagent-0.5.0-SNAPSHOT-all.jar",
        "-Dota.exporter=logging",
//        "-Dio.opentelemetry.auto.slf4j.simpleLogger.defaultLogLevel=debug"
    })
public class StorageBenchmark {

  @Benchmark
  public int noStorage() {
    return noStorageInternal(0);
  }

  private int noStorageInternal(int input){
    if (input < 500) {
      return noStorageInternal(input + 1);
    }
    return input;
  }

  @Benchmark
  public int context() {
    return contextInternal(0);
  }

  private int contextInternal(int input){
    if (input < 500) {
      return contextInternal(input + 1);
    }
    return input;
  }

  @Benchmark
  public int field() {
    return fieldInternal(0);
  }

  private int fieldInternal(int input){
    if (input < 500) {
      return fieldInternal(input + 1);
    }
    return input;
  }
}
