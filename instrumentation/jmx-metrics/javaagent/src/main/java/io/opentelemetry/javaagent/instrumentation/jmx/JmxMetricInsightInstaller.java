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
import io.opentelemetry.instrumentation.api.config.IncludeExcludeBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.instrumentation.jmx.internal.InternalMetricsDefinitions;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/** An {@link AgentListener} that enables JMX metrics during agent startup. */
@AutoService(AgentListener.class)
public class JmxMetricInsightInstaller implements AgentListener {

  private static final Logger logger = Logger.getLogger(JmxMetricInsightInstaller.class.getName());
  private static final String EXPERIMENTAL_PREFIX = "experimental-";

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "jmx");

    if (!config.getBoolean("enabled", true)) {
      return;
    }

    JmxTelemetryBuilder jmx =
        JmxTelemetry.builder(GlobalOpenTelemetry.get())
            .beanDiscoveryDelay(
                Duration.ofMillis(
                    config.get("discovery").getLong("delay", Duration.ofMinutes(1).toMillis())));

    config.getScalarList("config", String.class, emptyList()).stream()
        .map(Paths::get)
        .forEach(path -> addFileRules(path, jmx));

    // otel.jmx.target.system support will be removed in v3
    List<String> systemsConfig =
        config.get("target").getScalarList("system", String.class, emptyList());
    if (!systemsConfig.isEmpty()) {
      logger.log(
          WARNING,
          "'otel.jmx.target.system' is deprecated and will be removed in 3.x. Use 'otel.jmx.experimental.include' with v3 preview instead.");
    }

    boolean v3Preview = AgentCommonConfig.get().isV3Preview();
    if (v3Preview) {
      // include all stable metrics excepted for jvm metrics as they overlap runtime-telemetry
      jmx.addStableMetrics(IncludeExclude.builder().setExcluded("jvm").build());

      List<String> unstableInclude =
          config.get("experimental").getScalarList("included", String.class, emptyList());
      IncludeExcludeBuilder builder = IncludeExclude.builder();
      if (!unstableInclude.isEmpty()) {
        // only include explicitly opted-in, others will be excluded
        builder.setIncluded(unstableInclude);
      }
      jmx.addUnstableMetrics(builder.build());

    } else {
      // pre-v3 compatibility

      // mapping of 'experimental-' deprecated prefix in target system
      systemsConfig =
          systemsConfig.stream()
              .map(
                  s ->
                      s.startsWith(EXPERIMENTAL_PREFIX)
                          ? s.substring(EXPERIMENTAL_PREFIX.length())
                          : s)
              .collect(toList());

      // warn about unsupported systems
      systemsConfig.forEach(
          system -> {
            if (!InternalMetricsDefinitions.getSupportedSystems().contains(system)) {
              logger.log(
                  WARNING,
                  "JMX target system "
                      + system
                      + " is not supported. Supported systems are: "
                      + InternalMetricsDefinitions.getSupportedSystems());
            }
          });

      // Using the same filter to include both the stable and unstable metrics allowing to load
      // all embedded metrics definitions per system.
      IncludeExclude systems = IncludeExclude.builder().setIncluded(systemsConfig).build();
      jmx.addStableMetrics(systems).addUnstableMetrics(systems);
    }

    // include/exclude metrics by name
    List<String> metricsInclude =
        config.get("metrics").getScalarList("included", String.class, emptyList());
    List<String> metricsExclude =
        config.get("metrics").getScalarList("excluded", String.class, emptyList());
    if (!metricsInclude.isEmpty() || !metricsExclude.isEmpty()) {
      jmx.setMetrics(
          IncludeExclude.builder()
              .setIncluded(metricsInclude)
              .setExcluded(metricsExclude)
              .build());
    }

    jmx.build().start();
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
