/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents a manual telemetry entry that can be specified directly in metadata.yaml files. This
 * allows instrumentations to document their telemetry without relying solely on test-based
 * collection. This class is internal and is hence not for public use. Its APIs are unstable and can
 * change at any time.
 */
public class ManualTelemetryEntry {
  private String when = "default";
  private List<ManualMetric> metrics = new ArrayList<>();
  private List<ManualSpan> spans = new ArrayList<>();

  public ManualTelemetryEntry() {}

  public ManualTelemetryEntry(
      String when, @Nullable List<ManualMetric> metrics, @Nullable List<ManualSpan> spans) {
    this.when = when;
    this.metrics = copyList(metrics);
    this.spans = copyList(spans);
  }

  public String getWhen() {
    return when;
  }

  public void setWhen(String when) {
    this.when = when;
  }

  public List<ManualMetric> getMetrics() {
    return metrics;
  }

  public void setMetrics(@Nullable List<ManualMetric> metrics) {
    this.metrics = copyList(metrics);
  }

  public List<ManualSpan> getSpans() {
    return spans;
  }

  public void setSpans(@Nullable List<ManualSpan> spans) {
    this.spans = copyList(spans);
  }

  /**
   * Represents a manually specified metric. This class is internal and is hence not for public use.
   * Its APIs are unstable and can change at any time.
   */
  public static class ManualMetric {
    private String name = "";
    private String description = "";
    private String type = "";
    private String unit = "";
    private List<TelemetryAttribute> attributes = new ArrayList<>();

    public ManualMetric() {}

    public ManualMetric(
        String name,
        String description,
        String type,
        String unit,
        @Nullable List<TelemetryAttribute> attributes) {
      this.name = name;
      this.description = description;
      this.type = type;
      this.unit = unit;
      this.attributes = copyList(attributes);
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

    public void setAttributes(@Nullable List<TelemetryAttribute> attributes) {
      this.attributes = copyList(attributes);
    }
  }

  /**
   * Represents a manually specified span. This class is internal and is hence not for public use.
   * Its APIs are unstable and can change at any time.
   */
  public static class ManualSpan {
    @JsonProperty("span_kind")
    private String spanKind = "";

    private List<TelemetryAttribute> attributes = new ArrayList<>();

    public ManualSpan() {}

    public ManualSpan(String spanKind, @Nullable List<TelemetryAttribute> attributes) {
      this.spanKind = spanKind;
      this.attributes = copyList(attributes);
    }

    @JsonProperty("span_kind")
    public String getSpanKind() {
      return spanKind;
    }

    @JsonProperty("span_kind")
    public void setSpanKind(String spanKind) {
      this.spanKind = spanKind;
    }

    public List<TelemetryAttribute> getAttributes() {
      return attributes;
    }

    public void setAttributes(@Nullable List<TelemetryAttribute> attributes) {
      this.attributes = copyList(attributes);
    }
  }

  private static <T> List<T> copyList(@Nullable List<T> values) {
    return values == null ? new ArrayList<>() : new ArrayList<>(values);
  }
}
