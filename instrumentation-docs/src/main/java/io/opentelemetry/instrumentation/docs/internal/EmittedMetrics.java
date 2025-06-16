/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of metrics emitted by an instrumentation. Includes context about whether emitted
 * by default or via a configuration option. This class is internal and is hence not for public use.
 * Its APIs are unstable and can change at any time.
 */
public class EmittedMetrics {
  // Condition in which the metrics are emitted (ex: default, or configuration option names).
  private String when;
  private List<Metric> metrics;

  public EmittedMetrics() {
    this.when = "";
    this.metrics = new ArrayList<>();
  }

  public EmittedMetrics(String when, List<Metric> metrics) {
    this.when = "";
    this.metrics = metrics;
  }

  public String getWhen() {
    return when;
  }

  public void setWhen(String when) {
    this.when = when;
  }

  public List<Metric> getMetrics() {
    return metrics;
  }

  public void setMetrics(List<Metric> metrics) {
    this.metrics = metrics;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Metric {
    private String name;
    private String description;
    private String type;
    private String unit;
    private List<TelemetryAttribute> attributes;

    public Metric(
        String name, String description, String type, String unit, List<TelemetryAttribute> attributes) {
      this.name = name;
      this.description = description;
      this.type = type;
      this.unit = unit;
      this.attributes = attributes;
    }

    public Metric() {
      this.name = "";
      this.description = "";
      this.type = "";
      this.unit = "";
      this.attributes = new ArrayList<>();
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getUnit() {
      return unit;
    }

    public void setUnit(String unit) {
      this.unit = unit;
    }

    public List<TelemetryAttribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(List<TelemetryAttribute> attributes) {
      this.attributes = attributes;
    }
  }
}
