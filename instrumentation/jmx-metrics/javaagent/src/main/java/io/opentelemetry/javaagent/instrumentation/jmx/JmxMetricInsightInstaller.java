/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/** An {@link AgentListener} that enables JMX metrics during agent startup. */
@AutoService(AgentListener.class)
public class JmxMetricInsightInstaller implements AgentListener {

  private static final Logger logger = Logger.getLogger(JmxMetricInsightInstaller.class.getName());

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    OpenTelemetry openTelemetry = autoConfiguredSdk.getOpenTelemetrySdk();
    ConfigProperties config = AutoConfigureUtil.getConfig(autoConfiguredSdk);

    boolean defaultEnabled =
        DeclarativeConfigUtil.getBoolean(openTelemetry, "java", "common", "default_enabled")
            .orElse(true);
    boolean enabled =
        DeclarativeConfigUtil.getBoolean(openTelemetry, "java", "jmx", "enabled")
            .orElse(defaultEnabled);
    if (enabled) {
      JmxTelemetryBuilder jmx =
          JmxTelemetry.builder(GlobalOpenTelemetry.get())
              .beanDiscoveryDelay(beanDiscoveryDelay(openTelemetry, config));

      DeclarativeConfigUtil.getList(openTelemetry, "java", "jmx", "config")
          .orElse(Collections.emptyList())
          .stream()
          .map(Paths::get)
          .forEach(path -> addFileRules(path, jmx));
      DeclarativeConfigUtil.getList(openTelemetry, "java", "jmx", "target_system")
          .orElse(Collections.emptyList())
          .forEach(target -> addClasspathRules(target, jmx));

      jmx.build().start();
    }
  }

  private static void addFileRules(Path path, JmxTelemetryBuilder builder) {
    try {
      builder.addRules(path);
    } catch (RuntimeException e) {
      // for now only log JMX metric configuration errors as they do not prevent agent startup
      logger.log(Level.SEVERE, "Error while loading JMX configuration from " + path, e);
    }
  }

  private static void addClasspathRules(String target, JmxTelemetryBuilder builder) {
    ClassLoader classLoader = JmxTelemetryBuilder.class.getClassLoader();
    String resource = String.format("jmx/rules/%s.yaml", target);
    InputStream input = classLoader.getResourceAsStream(resource);
    try {
      builder.addRules(input);
    } catch (RuntimeException e) {
      // for now only log JMX metric configuration errors as they do not prevent agent startup
      logger.log(
          Level.SEVERE, "Error while loading JMX configuration from classpath " + resource, e);
    }
  }

  private static Duration beanDiscoveryDelay(
      OpenTelemetry openTelemetry, ConfigProperties configProperties) {
    Duration discoveryDelay =
        DeclarativeConfigUtil.getDuration(openTelemetry, "java", "jmx", "discovery_delay")
            .orElse(null);
    if (discoveryDelay != null) {
      return discoveryDelay;
    }

    // If discovery delay has not been configured, have a peek at the metric export interval.
    // It makes sense for both of these values to be similar.
    return configProperties.getDuration("otel.metric.export.interval", Duration.ofMinutes(1));
  }
}
