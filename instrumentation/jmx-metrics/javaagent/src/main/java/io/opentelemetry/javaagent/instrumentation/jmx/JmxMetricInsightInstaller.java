/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig.Stability.STABLE;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.SEVERE;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
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
  private static final String INSTRUMENTATION_NAME = "jmx";

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME);

    if (!AgentDistributionConfig.get().isInstrumentationEnabled(INSTRUMENTATION_NAME)) {
      return;
    }

    JmxTelemetryBuilder jmx =
        JmxTelemetry.builder(GlobalOpenTelemetry.get())
            .beanDiscoveryDelay(
                Duration.ofMillis(
                    config.get("discovery").getLong("delay", Duration.ofMinutes(1).toMillis())));

    configure(jmx, config);
    jmx.build().start();
  }

  // visible for testing
  static void configure(JmxTelemetryBuilder jmx, DeclarativeConfigProperties config) {
    config.getScalarList("config", String.class, emptyList()).stream()
        .map(Paths::get)
        .forEach(path -> addFileRules(path, jmx));

    List<String> unstableInclude =
        config
            .get("metrics")
            .get("experimental")
            .getScalarList("included", String.class, emptyList());
    // JVM rules overlap with runtime-telemetry.
    jmx.setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    jmx.setInternalMetricsUnstableMetricsFilter(
        unstableInclude.isEmpty()
            ? IncludeExclude.builder().setExcluded("*").build()
            : IncludeExclude.builder().setIncluded(unstableInclude).build());

    // include/exclude metrics by name
    IncludeExclude metrics =
        SelectorConfig.resolve(config, INSTRUMENTATION_NAME, "metrics", STABLE);
    if (metrics != null) {
      jmx.setMetrics(metrics);
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
