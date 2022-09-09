/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A class responsible for maintaining the current configuration for JMX metrics to be collected.
 */
public class MetricConfiguration {

  private final Collection<MetricDef> currentSet = new ArrayList<>();

  public MetricConfiguration() {}

  public boolean isEmpty() {
    return currentSet.isEmpty();
  }

  public void addMetricDef(MetricDef def) {
    currentSet.add(def);
  }

  Collection<MetricDef> getMetricDefs() {
    return currentSet;
  }
}
