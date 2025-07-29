/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Representation of spans emitted by an instrumentation. Includes context about whether emitted by
 * default or via a configuration option. This class is internal and is hence not for public use.
 * Its APIs are unstable and can change at any time.
 */
public class EmittedSpans {
  // Condition in which the telemetry is emitted (ex: default, or configuration option names).
  private String when;

  @JsonProperty("spans_by_scope")
  private List<SpansByScope> spansByScope;

  public EmittedSpans() {
    this.when = "";
    this.spansByScope = emptyList();
  }

  public EmittedSpans(String when, List<SpansByScope> spansByScope) {
    this.when = when;
    this.spansByScope = spansByScope;
  }

  public String getWhen() {
    return when;
  }

  public void setWhen(String when) {
    this.when = when;
  }

  @JsonProperty("spans_by_scope")
  public List<SpansByScope> getSpansByScope() {
    return spansByScope;
  }

  @JsonProperty("spans_by_scope")
  public void setSpansByScope(List<SpansByScope> spans) {
    this.spansByScope = spans;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class SpansByScope {
    private String scope;
    private List<Span> spans;

    public SpansByScope(String scopeName, List<Span> spans) {
      this.scope = scopeName;
      this.spans = spans;
    }

    public SpansByScope() {
      this.scope = "";
      this.spans = emptyList();
    }

    public String getScope() {
      return scope;
    }

    public void setScope(String scope) {
      this.scope = scope;
    }

    public List<Span> getSpans() {
      return spans;
    }

    public void setSpans(List<Span> spans) {
      this.spans = spans;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Span {
    @JsonProperty("span_kind")
    private String spanKind;

    private List<TelemetryAttribute> attributes;

    public Span(String spanKind, List<TelemetryAttribute> attributes) {
      this.spanKind = spanKind;
      this.attributes = attributes;
    }

    public Span() {
      this.spanKind = "";
      this.attributes = new ArrayList<>();
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

    public void setAttributes(List<TelemetryAttribute> attributes) {
      this.attributes = attributes;
    }
  }
}
