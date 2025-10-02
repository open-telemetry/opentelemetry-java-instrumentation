/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

public class SmokeTestOptions<T> {

  Function<T, String> getImage;
  String[] command;
  String jvmArgsEnvVarName = "JAVA_TOOL_OPTIONS";
  boolean setServiceName = true;
  final Map<String, String> extraEnv = new HashMap<>();
  List<ResourceMapping> extraResources = List.of();
  TargetWaitStrategy waitStrategy;
  List<Integer> extraPorts = List.of();
  Duration telemetryTimeout = Duration.ofSeconds(30);

  /** Sets the container image to run. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> image(Function<T, String> getImage) {
    this.getImage = getImage;
    return this;
  }

  /** Configure test for spring boot test app. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> springBoot(String imageTag) {
    image(
        jdk ->
            String.format(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk%s-%s",
                jdk, imageTag));
    waitStrategy(
        new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started SpringbootApplication in.*"));
    return this;
  }

  /** Sets the command to run in the target container. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> command(String... command) {
    this.command = command;
    return this;
  }

  /** Sets the environment variable name used to pass JVM arguments to the target application. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> jvmArgsEnvVarName(String jvmArgsEnvVarName) {
    this.jvmArgsEnvVarName = jvmArgsEnvVarName;
    return this;
  }

  /** Enables or disables setting the default service name for the target application. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> setServiceName(boolean setServiceName) {
    this.setServiceName = setServiceName;
    return this;
  }

  /** Adds an environment variable to the target application's environment. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> env(String key, String value) {
    this.extraEnv.put(key, value);
    return this;
  }

  /** Specifies additional files to copy to the target container. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> extraResources(ResourceMapping... resources) {
    this.extraResources = List.of(resources);
    return this;
  }

  /** Sets the wait strategy for the target container startup. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> waitStrategy(@Nullable TargetWaitStrategy waitStrategy) {
    this.waitStrategy = waitStrategy;
    return this;
  }

  /** Specifies additional ports to expose from the target container. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> extraPorts(Integer... ports) {
    this.extraPorts = List.of(ports);
    return this;
  }

  /** Sets the timeout duration for retrieving telemetry data. */
  @CanIgnoreReturnValue
  public SmokeTestOptions<T> telemetryTimeout(Duration telemetryTimeout) {
    this.telemetryTimeout = telemetryTimeout;
    return this;
  }
}
