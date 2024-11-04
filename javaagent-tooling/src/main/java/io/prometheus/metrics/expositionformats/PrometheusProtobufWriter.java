/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.prometheus.metrics.expositionformats;

import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Replacement for prometheus protobuf writer that does not depend on any of the protobuf classes.
 * This allows us to exclude io.prometheus:prometheus-metrics-shaded-protobuf dependency.
 *
 * @see <a href="https://github.com/prometheus/client_java/blob/main/prometheus-metrics-exposition-formats/src/main/java/io/prometheus/metrics/expositionformats/PrometheusProtobufWriter.java">PrometheusProtobufWriter</a>
 */
public class PrometheusProtobufWriter implements ExpositionFormatWriter {

  @Override
  public boolean accepts(String acceptHeader) {
    return false;
  }

  @Override
  public void write(OutputStream out, MetricSnapshots metricSnapshots) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getContentType() {
    throw new UnsupportedOperationException();
  }
}
