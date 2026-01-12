/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/** An {@link AgentListener} that enables JMX metrics during agent startup. */
@AutoService(AgentListener.class)
public class JmxMetricInsightInstaller implements AgentListener {

  private static final Logger logger = Logger.getLogger(JmxMetricInsightInstaller.class.getName());

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ExtendedDeclarativeConfigProperties config =
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
      config
          .getScalarList("target_system", String.class, emptyList())
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
}
