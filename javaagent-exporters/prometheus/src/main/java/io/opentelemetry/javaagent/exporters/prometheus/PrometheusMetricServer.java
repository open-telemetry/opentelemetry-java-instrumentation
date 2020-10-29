/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.exporters.prometheus;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporters.prometheus.PrometheusCollector;
import io.opentelemetry.javaagent.spi.exporter.MetricServer;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PrometheusMetricServer} registers {@link MetricProducer} to the {@link
 * PrometheusCollector}. The collector pulls metrics from the producer when the Prometheus server
 * scraps metric endpoint.
 */
@AutoService(MetricServer.class)
public class PrometheusMetricServer implements MetricServer {
  private static final Logger log = LoggerFactory.getLogger(PrometheusMetricServer.class);

  private static final String EXPORTER_NAME = "otel.exporter.prometheus";
  private static final String PORT_CONF_PROP_NAME = EXPORTER_NAME + ".port";
  private static final String HOST_CONF_PROP_NAME = EXPORTER_NAME + ".host";
  private static final String DEFAULT_PORT = "9464";
  // The empty address equals to any address
  private static final String DEFAULT_HOST = "0.0.0.0";

  @Override
  public void start(MetricProducer producer, Properties config) {
    PrometheusCollector.builder().setMetricProducer(producer).buildAndRegister();
    try {
      String portStr = config.getProperty(PORT_CONF_PROP_NAME, DEFAULT_PORT);
      String host = config.getProperty(HOST_CONF_PROP_NAME, DEFAULT_HOST);
      log.info("Creating Prometheus exporter on host: '{}' and port: '{}'", host, portStr);
      // Prometheus HTTP server uses global registry configured by PrometheusCollector.
      new HTTPServer(host, Integer.parseInt(portStr), true);
    } catch (IOException e) {
      log.error("Failed to create Prometheus server", e);
    }
  }

  @Override
  public Set<String> getNames() {
    return Collections.singleton("prometheus");
  }
}
