/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest

class MetricsInspector {
  final Collection<ExportMetricsServiceRequest> requests

  MetricsInspector(Collection<ExportMetricsServiceRequest> requests) {
    this.requests = requests
  }

  boolean hasMetricsNamed(String metricName) {
    requests.stream()
      .flatMap({ it.resourceMetricsList.stream() })
      .flatMap({ it.scopeMetricsList.stream() })
      .flatMap({ it.metricsList.stream() })
      .anyMatch({ it.name == metricName })
  }
}
