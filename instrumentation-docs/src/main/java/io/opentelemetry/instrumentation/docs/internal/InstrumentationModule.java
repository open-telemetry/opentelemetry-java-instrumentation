/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private InstrumentationScopeInfo scopeInfo;
  private Map<String, List<EmittedMetrics.Metric>> metrics;
  private Map<String, List<EmittedSpans.Span>> spans;
  private boolean hasStandaloneLibrary;

  @Nullable private Set<String> agentTargetVersions;

  @Nullable private Integer minJavaVersion;

  @Nullable private InstrumentationMetadata metadata;

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public InstrumentationModule(Builder builder) {
    this.instrumentationName =
        requireNonNull(builder.instrumentationName, "instrumentationName required");
    this.srcPath =
        requireNonNullElse(builder.srcPath, "instrumentation/" + builder.instrumentationName);
    this.namespace = requireNonNullElse(builder.namespace, builder.instrumentationName);
    this.group = requireNonNullElse(builder.group, builder.instrumentationName);
    this.metrics = requireNonNullElseGet(builder.metrics, HashMap::new);
    this.spans = requireNonNullElseGet(builder.spans, HashMap::new);
    this.hasStandaloneLibrary = builder.hasStandaloneLibrary;
    this.metadata = builder.metadata;
    this.agentTargetVersions = builder.agentTargetVersions;
    this.minJavaVersion = builder.minJavaVersion;
    this.scopeInfo =
        requireNonNullElseGet(
            builder.scopeInfo,
            () -> InstrumentationScopeInfo.create("io.opentelemetry." + instrumentationName));
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

  public boolean hasStandaloneLibrary() {
    return hasStandaloneLibrary;
  }

  public String getGroup() {
    return group;
  }

  public InstrumentationScopeInfo getScopeInfo() {
    return scopeInfo;
  }

  public InstrumentationMetadata getMetadata() {
    if (metadata == null) {
      metadata = new InstrumentationMetadata();
    }

    return metadata;
  }

  @Nullable
  public Set<String> getAgentTargetVersions() {
    return agentTargetVersions;
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

  public void setAgentTargetVersions(Set<String> agentTargetVersions) {
    this.agentTargetVersions = agentTargetVersions;
  }

  public void setScopeInfo(InstrumentationScopeInfo scopeInfo) {
    this.scopeInfo = scopeInfo;
  }

  public void setMetadata(InstrumentationMetadata metadata) {
    this.metadata = metadata;
  }

  public void setHasStandaloneLibrary(boolean hasStandaloneLibrary) {
    this.hasStandaloneLibrary = hasStandaloneLibrary;
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
    @Nullable private InstrumentationMetadata metadata;
    @Nullable private InstrumentationScopeInfo scopeInfo;
    @Nullable private Set<String> agentTargetVersions;
    @Nullable private Map<String, List<EmittedMetrics.Metric>> metrics;
    @Nullable private Map<String, List<EmittedSpans.Span>> spans;
    private boolean hasStandaloneLibrary;

    public Builder() {}

    public Builder(String instrumentationName) {
      this.instrumentationName = instrumentationName;
    }

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
    public Builder hasStandaloneLibrary(boolean hasStandaloneLibrary) {
      this.hasStandaloneLibrary = hasStandaloneLibrary;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder scope(InstrumentationScopeInfo scope) {
      this.scopeInfo = scope;
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
    public Builder metadata(InstrumentationMetadata metadata) {
      this.metadata = metadata;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder targetVersions(Set<String> agentTargetVersions) {
      this.agentTargetVersions = agentTargetVersions;
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
