/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Represents an instrumentation module and all associated metadata.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class InstrumentationModule {
  private final String srcPath;
  private final String instrumentationName;
  private final String namespace;
  private final String group;
  private final InstrumentationScopeInfo scopeInfo;
  private Map<String, List<EmittedMetrics.Metric>> metrics;
  private Map<String, List<EmittedSpans.Span>> spans;

  @Nullable private Map<InstrumentationType, Set<String>> targetVersions;

  @Nullable private Integer minJavaVersion;

  @Nullable private InstrumentationMetaData metadata;

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public InstrumentationModule(Builder builder) {
    requireNonNull(builder.srcPath, "srcPath required");
    requireNonNull(builder.instrumentationName, "instrumentationName required");
    requireNonNull(builder.namespace, "namespace required");
    requireNonNull(builder.group, "group required");

    this.metrics = Objects.requireNonNullElseGet(builder.metrics, HashMap::new);
    this.spans = Objects.requireNonNullElseGet(builder.spans, HashMap::new);
    this.srcPath = builder.srcPath;
    this.instrumentationName = builder.instrumentationName;
    this.namespace = builder.namespace;
    this.group = builder.group;
    this.metadata = builder.metadata;
    this.targetVersions = builder.targetVersions;
    this.minJavaVersion = builder.minJavaVersion;
    this.scopeInfo = InstrumentationScopeInfo.create("io.opentelemetry." + instrumentationName);
  }

  public String getSrcPath() {
    return srcPath;
  }

  public String getInstrumentationName() {
    return instrumentationName;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getGroup() {
    return group;
  }

  public InstrumentationScopeInfo getScopeInfo() {
    return scopeInfo;
  }

  public InstrumentationMetaData getMetadata() {
    if (metadata == null) {
      metadata = new InstrumentationMetaData();
    }

    return metadata;
  }

  @Nullable
  public Map<InstrumentationType, Set<String>> getTargetVersions() {
    return targetVersions;
  }

  @Nullable
  public Integer getMinJavaVersion() {
    return minJavaVersion;
  }

  public Map<String, List<EmittedMetrics.Metric>> getMetrics() {
    return metrics;
  }

  public Map<String, List<EmittedSpans.Span>> getSpans() {
    return spans;
  }

  public void setTargetVersions(Map<InstrumentationType, Set<String>> targetVersions) {
    this.targetVersions = targetVersions;
  }

  public void setMetadata(InstrumentationMetaData metadata) {
    this.metadata = metadata;
  }

  public void setMinJavaVersion(Integer minJavaVersion) {
    this.minJavaVersion = minJavaVersion;
  }

  public void setMetrics(Map<String, List<EmittedMetrics.Metric>> metrics) {
    this.metrics = metrics;
  }

  public void setSpans(Map<String, List<EmittedSpans.Span>> spans) {
    this.spans = spans;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Builder {
    @Nullable private String srcPath;
    @Nullable private String instrumentationName;
    @Nullable private String namespace;
    @Nullable private String group;
    @Nullable private Integer minJavaVersion;
    @Nullable private InstrumentationMetaData metadata;
    @Nullable private Map<InstrumentationType, Set<String>> targetVersions;
    @Nullable private Map<String, List<EmittedMetrics.Metric>> metrics;
    @Nullable private Map<String, List<EmittedSpans.Span>> spans;

    @CanIgnoreReturnValue
    public Builder srcPath(String srcPath) {
      this.srcPath = srcPath;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder instrumentationName(String instrumentationName) {
      this.instrumentationName = instrumentationName;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder minJavaVersion(Integer minJavaVersion) {
      this.minJavaVersion = minJavaVersion;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder group(String group) {
      this.group = group;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder metadata(InstrumentationMetaData metadata) {
      this.metadata = metadata;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder targetVersions(Map<InstrumentationType, Set<String>> targetVersions) {
      this.targetVersions = targetVersions;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder metrics(Map<String, List<EmittedMetrics.Metric>> metrics) {
      this.metrics = metrics;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder spans(Map<String, List<EmittedSpans.Span>> spans) {
      this.spans = spans;
      return this;
    }

    public InstrumentationModule build() {
      return new InstrumentationModule(this);
    }
  }
}
