/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractTestContainerManager implements TestContainerManager {
  protected static final int TARGET_PORT = 8080;
  protected static final int BACKEND_PORT = 8080;

  protected static final String BACKEND_ALIAS = "backend";
  protected static final String TARGET_AGENT_FILENAME = "opentelemetry-javaagent.jar";

  private boolean started = false;

  protected Map<String, String> getAgentEnvironment(String jvmArgsEnvVarName) {
    Map<String, String> environment = new HashMap<>();
    // while modern JVMs understand linux container memory limits, they do not understand windows
    // container memory limits yet, so we need to explicitly set max heap in order to prevent the
    // JVM from taking too much memory and hitting the windows container memory limit
    environment.put(jvmArgsEnvVarName, "-Xms1g -Xmx1g -javaagent:/" + TARGET_AGENT_FILENAME);
    environment.put("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1");
    environment.put("OTEL_BSP_SCHEDULE_DELAY", "10ms");
    environment.put("OTEL_METRIC_EXPORT_INTERVAL", "1000");
    environment.put("OTEL_EXPORTER_OTLP_ENDPOINT", "http://" + BACKEND_ALIAS + ":8080");
    environment.put("OTEL_RESOURCE_ATTRIBUTES", "service.name=smoke-test");
    environment.put("OTEL_JAVAAGENT_DEBUG", "true");
    return environment;
  }

  protected abstract void startEnvironment();

  protected abstract void stopEnvironment();

  @Override
  public void startEnvironmentOnce() {
    if (!started) {
      started = true;
      startEnvironment();
      Runtime.getRuntime().addShutdownHook(new Thread(this::stopEnvironment));
    }
  }
}
