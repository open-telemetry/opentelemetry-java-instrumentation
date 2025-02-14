/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.context;

import javax.annotation.Nullable;

public class TraceContextHolder {

  @Nullable private String traceIdKey;

  @Nullable private String spanIdKey;
  @Nullable private String traceId;
  @Nullable private String spanId;
  @Nullable private String parentSpanId;

  public TraceContextHolder() {}

  public TraceContextHolder(
      @Nullable String traceIdKey, @Nullable String spanIdKey, @Nullable String traceId,
      @Nullable String spanId, @Nullable String parentSpanId) {
    this.traceIdKey = traceIdKey;
    this.spanIdKey = spanIdKey;
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
  }

  @Nullable
  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  @Nullable
  public String getSpanId() {
    return spanId;
  }

  public void setSpanId(String spanId) {
    this.spanId = spanId;
  }

  @Nullable
  public String getParentSpanId() {
    return parentSpanId;
  }

  public void setParentSpanId(String parentSpanId) {
    this.parentSpanId = parentSpanId;
  }

  @Nullable
  public String getTraceIdKey() {
    return traceIdKey;
  }

  public void setTraceIdKey(String traceIdKey) {
    this.traceIdKey = traceIdKey;
  }

  @Nullable
  public String getSpanIdKey() {
    return spanIdKey;
  }

  public void setSpanIdKey(String spanIdKey) {
    this.spanIdKey = spanIdKey;
  }
}
