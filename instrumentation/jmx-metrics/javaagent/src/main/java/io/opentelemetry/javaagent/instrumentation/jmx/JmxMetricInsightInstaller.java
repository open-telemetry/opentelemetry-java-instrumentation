/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static java.util.Collections.emptyList;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/** An {@link AgentListener} that enables JMX metrics during agent startup. */
@AutoService(AgentListener.class)
public class JmxMetricInsightInstaller implements AgentListener {

  private static final Logger logger = Logger.getLogger(JmxMetricInsightInstaller.class.getName());

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jmx");

    if (config.getBoolean("enabled", true)) {
      JmxTelemetryBuilder jmx =
          JmxTelemetry.builder(GlobalOpenTelemetry.get())
              .beanDiscoveryDelay(
                  Duration.ofMillis(
                      config.get("discovery").getLong("delay", Duration.ofMinutes(1).toMillis())));

      config.getScalarList("config", String.class, emptyList()).stream()
          .map(Paths::get)
          .forEach(path -> addFileRules(path, jmx));

      boolean v3Preview = AgentCommonConfig.get().isV3Preview();
      List<String> systemsConfig =
          config.get("target").getScalarList("system", String.class, emptyList()).stream()
              .map(
                  target -> {
                    if (target.equals("kafka-broker") && !v3Preview) {
                      logger.log(
                          WARNING,
                          "The kafka-broker JMX target system has been renamed to experimental-kafka-broker.");
                      return "experimental-kafka-broker";
                    }
                    if (target.equals("kafka-connect") && !v3Preview) {
                      logger.log(
                          WARNING,
                          "The kafka-connect JMX target system has been renamed to experimental-kafka-connect.");
                      return "experimental-kafka-connect";
                    }
                    return target;
                  })
              .collect(toList());

      IncludeExclude systems =
          IncludeExclude.builder()
              .setIncluded(systemsConfig)
              // jvm metrics excluded in instrumentation as covered by runtime-telemetry
              .setExcluded("jvm")
              .build();

      jmx.addStableMetrics(systems).addUnstableMetrics(systems);

      jmx.build().start();
    }
  }

  private static void addFileRules(Path path, JmxTelemetryBuilder builder) {
    try {
      builder.addRules(path);
    } catch (RuntimeException e) {
      // for now only log JMX metric configuration errors as they do not prevent agent startup
      logger.log(SEVERE, "Error while loading JMX configuration from " + path, e);
    }
  }
}
