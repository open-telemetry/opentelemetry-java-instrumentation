/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.jmx.conf.yaml.RuleParser;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;

/** Collecting and exporting JMX metrics. */
public class MetricService {

  static final Logger logger = Logger.getLogger("JMX Metric Insight");

  private static final String INSTRUMENTATION_SCOPE = "io.opentelemetry.jmx";

  private final OpenTelemetry openTelemetry;
  private final ConfigProperties configProperties;

  public MetricService(OpenTelemetry ot, ConfigProperties config) {
    openTelemetry = ot;
    configProperties = config;
  }

  private static String resourceFor(String platform) {
    return "/jmx/rules/" + platform + ".yaml";
  }

  private void addRulesForPlatform(String platform, MetricConfiguration conf) {

    String yamlResource = resourceFor(platform);
    try (InputStream inputStream = getClass().getResourceAsStream(yamlResource)) {
      if (inputStream != null) {
        logger.log(FINE, "Opened input stream {0}", yamlResource);
        RuleParser parserInstance = RuleParser.get();
        parserInstance.addMetricDefs(logger, conf, inputStream);
      } else {
        logger.log(CONFIG, "No support found for {0}", platform);
      }
    } catch (Exception e) {
      logger.warning(e.getMessage());
    }
  }

  private void buildFromDefaultRules(MetricConfiguration conf) {

    String targetSystem = System.getProperty("otel.jmx.target.system", "").trim();
    String[] platforms = targetSystem.length() == 0 ? new String[0] : targetSystem.split(",");

    for (String platform : platforms) {
      addRulesForPlatform(platform, conf);
    }
  }

  private static void buildFromUserRules(MetricConfiguration conf) {
    String jmxDir = System.getProperty("otel.jmx.config");
    if (jmxDir != null) {
      logger.log(CONFIG, "JMX config file name: {0}", jmxDir);
      RuleParser parserInstance = RuleParser.get();
      try (InputStream inputStream = Files.newInputStream(new File(jmxDir.trim()).toPath())) {
        parserInstance.addMetricDefs(logger, conf, inputStream);
      } catch (Exception e) {
        logger.warning(e.getMessage());
      }
    }
  }

  private MetricConfiguration buildMetricConfiguration() {
    MetricConfiguration conf = new MetricConfiguration();

    buildFromDefaultRules(conf);

    buildFromUserRules(conf);

    return conf;
  }

  public void start() {
    MetricConfiguration conf = buildMetricConfiguration();

    if (conf.isEmpty()) {
      logger.log(
          CONFIG,
          "Empty JMX configuration, no metrics will be collected for InstrumentationScope "
              + INSTRUMENTATION_SCOPE);
    } else {
      MetricRegistrar registrar = new MetricRegistrar(openTelemetry, INSTRUMENTATION_SCOPE);
      BeanFinder finder = new BeanFinder(registrar, configProperties);
      finder.discoverBeans(conf);
    }
  }
}
