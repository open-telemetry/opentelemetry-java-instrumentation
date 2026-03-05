/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Representation of metrics emitted by an instrumentation. Includes context about whether emitted
 * by default or via a configuration option. This class is internal and is hence not for public use.
 * Its APIs are unstable and can change at any time.
 */
public class EmittedMetrics {
  // Condition in which the metrics are emitted (ex: default, or configuration option names).
  private String when;

  @JsonProperty("metrics_by_scope")
  private List<MetricsByScope> metricsByScope;

  public EmittedMetrics() {
    this.when = "";
    this.metricsByScope = emptyList();
  }

  public EmittedMetrics(String when, List<MetricsByScope> metricsByScope) {
    this.when = when;
    this.metricsByScope = metricsByScope;
  }

  public String getWhen() {
    return when;
  }

  public void setWhen(String when) {
    this.when = when;
  }

  @JsonProperty("metrics_by_scope")
  public List<MetricsByScope> getMetricsByScope() {
    return metricsByScope;
  }

  @JsonProperty("metrics_by_scope")
  public void setMetricsByScope(List<MetricsByScope> metricsByScope) {
    this.metricsByScope = metricsByScope;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class MetricsByScope {
    private String scope;
    private List<Metric> metrics;

    public MetricsByScope(String scope, List<Metric> metrics) {
      this.scope = scope;
      this.metrics = metrics;
    }

    public MetricsByScope() {
      this.scope = "";
      this.metrics = new ArrayList<>();
    }

    public String getScope() {
      return scope;
    }

    public void setScope(String scope) {
      this.scope = scope;
    }

    public List<Metric> getMetrics() {
      return metrics;
    }

    public void setMetrics(List<Metric> metrics) {
      this.metrics = metrics;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Metric {
    private String name;
    private String description;
    private String type;
    private String instrumentType;

    @Nullable
    @JsonProperty("is_monotonic")
    private Boolean isMonotonic;

    private String unit;
    private List<TelemetryAttribute> attributes;

    public Metric(
        String name,
        String description,
        String type,
        String unit,
        List<TelemetryAttribute> attributes) {
      this.name = name;
      this.description = description;
      this.type = type;
      this.instrumentType = "";
      this.isMonotonic = null;
      this.unit = unit;
      this.attributes = attributes;
    }

    public Metric(
        String name,
        String description,
        String type,
        String instrumentType,
        String unit,
        List<TelemetryAttribute> attributes) {
      this.name = name;
      this.description = description;
      this.type = type;
      this.instrumentType = instrumentType;
      this.isMonotonic = null;
      this.unit = unit;
      this.attributes = attributes;
    }

    public Metric(
        String name,
        String description,
        String type,
        String instrumentType,
        @Nullable Boolean isMonotonic,
        String unit,
        List<TelemetryAttribute> attributes) {
      this.name = name;
      this.description = description;
      this.type = type;
      this.instrumentType = instrumentType;
      this.isMonotonic = isMonotonic;
      this.unit = unit;
      this.attributes = attributes;
    }

    public Metric() {
      this.name = "";
      this.description = "";
      this.type = "";
      this.instrumentType = "";
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

    public String getInstrumentType() {
      return instrumentType;
    }

    public void setInstrumentType(String instrumentType) {
      this.instrumentType = instrumentType;
    }

    @Nullable
    @JsonProperty("is_monotonic")
    public Boolean getIsMonotonic() {
      return isMonotonic;
    }

    @JsonProperty("is_monotonic")
    public void setIsMonotonic(@Nullable Boolean isMonotonic) {
      this.isMonotonic = isMonotonic;
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

    /**
     * Builder for creating EmittedMetrics.Metric instances. This class is internal and is hence not
     * for public use. Its APIs are unstable and can change at any time.
     */
    public static class Builder {
      private String name = "";
      private String description = "";
      private String type = "";
      private String instrumentType = "";
      @Nullable private Boolean isMonotonic = null;
      private String unit = "";
      private List<TelemetryAttribute> attributes = new ArrayList<>();

      @CanIgnoreReturnValue
      public Builder name(String name) {
        this.name = name;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder description(String description) {
        this.description = description;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder type(String type) {
        this.type = type;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder instrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder isMonotonic(@Nullable Boolean isMonotonic) {
        this.isMonotonic = isMonotonic;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder unit(String unit) {
        this.unit = unit;
        return this;
      }

      @CanIgnoreReturnValue
      public Builder attributes(List<TelemetryAttribute> attributes) {
        this.attributes = attributes != null ? attributes : new ArrayList<>();
        return this;
      }

      public Metric build() {
        return new Metric(name, description, type, instrumentType, isMonotonic, unit, attributes);
      }
    }

    public static Builder builder() {
      return new Builder();
    }
  }
}
