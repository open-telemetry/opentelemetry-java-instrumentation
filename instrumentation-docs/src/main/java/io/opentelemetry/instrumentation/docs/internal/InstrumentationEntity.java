/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationEntity {
  private final String srcPath;
  private final String instrumentationName;
  private final String namespace;
  private final String group;
  private final InstrumentationScopeInfo scopeInfo;

  @Nullable private Map<InstrumentationType, Set<String>> targetVersions;

  @Nullable private Integer minJavaVersion;

  @Nullable private InstrumentationMetaData metadata;

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public InstrumentationEntity(Builder builder) {
    requireNonNull(builder.srcPath, "srcPath required");
    requireNonNull(builder.instrumentationName, "instrumentationName required");
    requireNonNull(builder.namespace, "namespace required");
    requireNonNull(builder.group, "group required");

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

  public void setTargetVersions(Map<InstrumentationType, Set<String>> targetVersions) {
    this.targetVersions = targetVersions;
  }

  public void setMetadata(InstrumentationMetaData metadata) {
    this.metadata = metadata;
  }

  public void setMinJavaVersion(Integer minJavaVersion) {
    this.minJavaVersion = minJavaVersion;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Builder {
    private String srcPath;
    private String instrumentationName;
    private String namespace;
    private String group;
    private Integer minJavaVersion;
    private InstrumentationMetaData metadata;
    private Map<InstrumentationType, Set<String>> targetVersions;

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

    public InstrumentationEntity build() {
      return new InstrumentationEntity(this);
    }
  }
}
