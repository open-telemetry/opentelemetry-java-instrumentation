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
import io.opentelemetry.javaagent.extension.internal.ConfigPropertiesUtil;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

/** An {@link AgentListener} that enables JMX metrics during agent startup. */
@AutoService(AgentListener.class)
public class JmxMetricInsightInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = ConfigPropertiesUtil.resolveConfigProperties(autoConfiguredSdk);

    if (config.getBoolean("otel.jmx.enabled", true)) {
      JmxMetricInsight service =
          JmxMetricInsight.createService(
              GlobalOpenTelemetry.get(), beanDiscoveryDelay(config).toMillis());
      MetricConfiguration conf = buildMetricConfiguration(config);
      service.startLocal(conf);
    }
  }

  private static Duration beanDiscoveryDelay(ConfigProperties configProperties) {
    Duration discoveryDelay = configProperties.getDuration("otel.jmx.discovery.delay");
    if (discoveryDelay != null) {
      return discoveryDelay;
    }

    // If discovery delay has not been configured, have a peek at the metric export interval.
    // It makes sense for both of these values to be similar.
    return configProperties.getDuration("otel.metric.export.interval", Duration.ofMinutes(1));
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
        parserInstance.addMetricDefsTo(conf, inputStream, platform);
      } else {
        JmxMetricInsight.getLogger().log(INFO, "No support found for {0}", platform);
      }
    } catch (Exception e) {
      JmxMetricInsight.getLogger().warning(e.getMessage());
    }
  }

  private static void buildFromDefaultRules(
      MetricConfiguration conf, ConfigProperties configProperties) {
    List<String> platforms = configProperties.getList("otel.jmx.target.system");
    for (String platform : platforms) {
      addRulesForPlatform(platform, conf);
    }
  }

  private static void buildFromUserRules(
      MetricConfiguration conf, ConfigProperties configProperties) {
    List<String> configFiles = configProperties.getList("otel.jmx.config");
    for (String configFile : configFiles) {
      JmxMetricInsight.getLogger().log(FINE, "JMX config file name: {0}", configFile);
      RuleParser parserInstance = RuleParser.get();
      try (InputStream inputStream = Files.newInputStream(Paths.get(configFile))) {
        parserInstance.addMetricDefsTo(conf, inputStream, configFile);
      } catch (Exception e) {
        // yaml parsing errors are caught and logged inside of addMetricDefsTo
        // only file access related exceptions are expected here
        JmxMetricInsight.getLogger().warning(e.toString());
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
