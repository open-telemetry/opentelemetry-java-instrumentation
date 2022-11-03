/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.jmx;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.jmx.engine.JmxMetricInsight;
import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import io.opentelemetry.instrumentation.jmx.yaml.RuleParser;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/** An {@link AgentListener} that enables JMX metrics during agent startup. */
@AutoService(AgentListener.class)
public class JmxMetricInsightInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = autoConfiguredSdk.getConfig();

    if (config.getBoolean("otel.jmx.enabled", true)) {
      JmxMetricInsight service =
          JmxMetricInsight.createService(GlobalOpenTelemetry.get(), beanDiscoveryDelay(config));
      MetricConfiguration conf = buildMetricConfiguration(config);
      service.start(conf);
    }
  }

  private static long beanDiscoveryDelay(ConfigProperties configProperties) {
    Long discoveryDelay = configProperties.getLong("otel.jmx.discovery.delay");
    if (discoveryDelay != null) {
      return discoveryDelay;
    }

    // If discovery delay has not been configured, have a peek at the metric export interval.
    // It makes sense for both of these values to be similar.
    long exportInterval = configProperties.getLong("otel.metric.export.interval", 60000);
    return exportInterval;
  }

  private static String resourceFor(String platform) {
    return "/jmx/rules/" + platform + ".yaml";
  }

  private static void addRulesForPlatform(String platform, MetricConfiguration conf) {
    String yamlResource = resourceFor(platform);
    try (InputStream inputStream =
        JmxMetricInsightInstaller.class.getResourceAsStream(yamlResource)) {
      if (inputStream != null) {
        JmxMetricInsight.getLogger().log(FINE, "Opened input stream {0}", yamlResource);
        RuleParser parserInstance = RuleParser.get();
        parserInstance.addMetricDefs(conf, inputStream);
      } else {
        JmxMetricInsight.getLogger().log(INFO, "No support found for {0}", platform);
      }
    } catch (Exception e) {
      JmxMetricInsight.getLogger().warning(e.getMessage());
    }
  }

  private static void buildFromDefaultRules(
      MetricConfiguration conf, ConfigProperties configProperties) {
    String targetSystem = configProperties.getString("otel.jmx.target.system", "");
    String[] platforms = targetSystem.isEmpty() ? new String[0] : targetSystem.split(",");

    for (String platform : platforms) {
      addRulesForPlatform(platform, conf);
    }
  }

  private static void buildFromUserRules(
      MetricConfiguration conf, ConfigProperties configProperties) {
    String jmxDir = configProperties.getString("otel.jmx.config");
    if (jmxDir != null) {
      JmxMetricInsight.getLogger().log(FINE, "JMX config file name: {0}", jmxDir);
      RuleParser parserInstance = RuleParser.get();
      try (InputStream inputStream = Files.newInputStream(new File(jmxDir.trim()).toPath())) {
        parserInstance.addMetricDefs(conf, inputStream);
      } catch (Exception e) {
        JmxMetricInsight.getLogger().warning(e.getMessage());
      }
    }
  }

  private static MetricConfiguration buildMetricConfiguration(ConfigProperties configProperties) {
    MetricConfiguration metricConfiguration = new MetricConfiguration();

    buildFromDefaultRules(metricConfiguration, configProperties);

    buildFromUserRules(metricConfiguration, configProperties);

    return metricConfiguration;
  }
}
