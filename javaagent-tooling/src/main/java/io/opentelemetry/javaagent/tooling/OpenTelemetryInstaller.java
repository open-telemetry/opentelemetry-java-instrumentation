/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(AgentListener.class)
public class OpenTelemetryInstaller implements AgentListener {
  private static final Logger log = LoggerFactory.getLogger(OpenTelemetryInstaller.class);

  static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";

  @Override
  public void beforeAgent(Config config) {
    installAgentTracer(config);
  }

  /**
   * Register agent tracer if no agent tracer is already registered.
   *
   * @param config Configuration instance
   */
  @SuppressWarnings("unused")
  public static synchronized void installAgentTracer(Config config) {
    if (config.getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true)) {
      copySystemProperties(config);

      OpenTelemetrySdk sdk = OpenTelemetrySdkAutoConfiguration.initialize();
      OpenTelemetrySdkAccess.internalSetForceFlush(
          (timeout, unit) -> sdk.getSdkTracerProvider().forceFlush().join(timeout, unit));
    } else {
      log.info("Tracing is disabled.");
    }
  }

  // OpenTelemetrySdkAutoConfiguration currently only supports configuration from environment. We
  // massage any properties we have that aren't in the environment to system properties.
  // TODO(anuraaga): Make this less hacky
  private static void copySystemProperties(Config config) {
    Map<String, String> allProperties = config.getAllProperties();
    Map<String, String> environmentProperties =
        Config.newBuilder()
            .readEnvironmentVariables()
            .readSystemProperties()
            .build()
            .getAllProperties();

    allProperties.forEach(
        (key, value) -> {
          if (!environmentProperties.containsKey(key)
              && key.startsWith("otel.")
              && !key.startsWith("otel.instrumentation")) {
            System.setProperty(key, value);
          }
        });
  }
}
