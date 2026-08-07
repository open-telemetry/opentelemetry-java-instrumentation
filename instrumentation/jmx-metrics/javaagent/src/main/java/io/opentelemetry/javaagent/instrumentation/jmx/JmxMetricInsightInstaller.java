/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static io.opentelemetry.instrumentation.jmx.internal.JmxTelemetryRules.locateRulesForSystem;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.instrumentation.jmx.internal.JmxTelemetryRules;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

      List<String> targets =
          config.get("target").getScalarList("system", String.class, emptyList());

      Set<String> rules = new HashSet<>();

      // lookup rules for selected system(s)
      ClassLoader classLoader = JmxTelemetryBuilder.class.getClassLoader();
      targets.stream()
          .map(target -> handleDeprecatedTargets(target, AgentCommonConfig.get().isV3Preview()))
          .forEach(
              target -> {
                Set<String> resources = locateRulesForSystem(classLoader, target, true);
                if (resources.isEmpty()) {
                  logger.log(SEVERE, "No JMX rules found for target system " + target);
                }
                rules.addAll(resources);
              });

      // lookup embedded rules if "auto" mode is enabled
      DeclarativeConfigProperties autoConfig = config.get("auto");
      boolean autoEnabled = autoConfig.getBoolean("enabled", false);
      if (autoEnabled) {
        Set<String> allSystems = new HashSet<>(JmxTelemetryRules.getSupportedSystems());
        // hasn't been moved to library yet, thus not included
        allSystems.add("experimental-kafka-broker");
        // should not be included as it overlaps with runtime-telemetry
        allSystems.remove("jvm");

        allSystems.stream()
            .map(system -> locateRulesForSystem(classLoader, system, true))
            .forEach(rules::addAll);
      }

      rules.forEach(rule -> addClasspathRules(classLoader, rule, jmx));

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

  private static void addClasspathRules(
      ClassLoader classLoader, String resource, JmxTelemetryBuilder builder) {
    try (InputStream input = classLoader.getResourceAsStream(resource)) {
      if (input == null) {
        logger.log(SEVERE, "JMX configuration not found on classpath " + resource);
        return;
      }
      builder.addRules(input);
    } catch (IOException | RuntimeException e) {
      // for now only log JMX metric configuration errors as they do not prevent agent startup
      logger.log(SEVERE, "Error while loading JMX configuration from classpath " + resource, e);
    }
  }

  private static String handleDeprecatedTargets(String target, boolean v3Preview) {
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
  }
}
