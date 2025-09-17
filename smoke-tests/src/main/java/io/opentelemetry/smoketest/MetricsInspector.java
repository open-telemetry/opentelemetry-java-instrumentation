/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import java.util.Collection;

public class MetricsInspector {
  final Collection<ExportMetricsServiceRequest> requests;

  public MetricsInspector(Collection<ExportMetricsServiceRequest> requests) {
    this.requests = requests;
  }

  public boolean hasMetricsNamed(String metricName) {
    return requests.stream()
        .flatMap(it -> it.getResourceMetricsList().stream())
        .flatMap(it -> it.getScopeMetricsList().stream())
        .flatMap(it -> it.getMetricsList().stream())
        .anyMatch(it -> metricName.equals(it.getName()));
  }
}
