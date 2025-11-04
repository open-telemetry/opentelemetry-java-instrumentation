/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.jmx;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
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
    ConfigProperties config = AutoConfigureUtil.getConfig(autoConfiguredSdk);

    if (config.getBoolean("otel.jmx.enabled", true)) {
      JmxTelemetryBuilder jmx =
          JmxTelemetry.builder(GlobalOpenTelemetry.get())
              .beanDiscoveryDelay(beanDiscoveryDelay(config).toMillis());

      try {
        config.getList("otel.jmx.config").stream().map(Paths::get).forEach(jmx::addCustomRules);
        config.getList("otel.jmx.target.system").forEach(jmx::addClasspathRules);
      } catch (IllegalArgumentException e) {
        // for now only log JMX errors as they do not prevent agent startup
        logger.log(Level.SEVERE, "Error while loading JMX configuration", e);
      }

      jmx.build().startLocal();
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
}
