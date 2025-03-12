/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EmittedMetrics {
  private List<Metric> metrics;

  public EmittedMetrics() {}

  public EmittedMetrics(List<Metric> metrics) {
    this.metrics = metrics;
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
    private List<Attribute> attributes;

    public Metric() {}

    public Metric(
        String name, String description, String type, String unit, List<Attribute> attributes) {
      this.name = name;
      this.description = description;
      this.type = type;
      this.unit = unit;
      this.attributes = attributes;
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

    public List<Attribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
      this.attributes = attributes;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Attribute {
    private String name;
    private String type;

    public Attribute() {}

    public Attribute(String name, String type) {
      this.name = name;
      this.type = type;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }
}
