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
  protected static final String COLLECTOR_ALIAS = "collector";
  protected static final String TARGET_AGENT_FILENAME = "opentelemetry-javaagent.jar";
  protected static final String COLLECTOR_CONFIG_RESOURCE = "/otel.yaml";

  private boolean started = false;

  protected Map<String, String> getAgentEnvironment(String jvmArgsEnvVarName) {
    Map<String, String> environment = new HashMap<>();
    environment.put(jvmArgsEnvVarName, "-javaagent:/" + TARGET_AGENT_FILENAME);
    environment.put("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1");
    environment.put("OTEL_BSP_SCHEDULE_DELAY", "10ms");
    environment.put("OTEL_IMR_EXPORT_INTERVAL", "1000");
    environment.put("OTEL_EXPORTER_OTLP_ENDPOINT", "http://" + COLLECTOR_ALIAS + ":55680");
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
      Runtime.getRuntime().addShutdownHook(new Thread(() -> stopEnvironment()));
    }
  }
}
