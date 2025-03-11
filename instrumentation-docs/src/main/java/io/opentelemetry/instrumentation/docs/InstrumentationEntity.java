/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.util.Map;
import java.util.Set;

public class InstrumentationEntity {
  private final String srcPath;
  private final String instrumentationName;
  private final String namespace;
  private final String group;

  private InstrumentationMetaData metadata;
  private Map<InstrumentationType, Set<String>> targetVersions;

  private InstrumentationScopeInfo scope;

  public InstrumentationEntity(
      String srcPath, String instrumentationName, String namespace, String group) {
    this.srcPath = srcPath;
    this.instrumentationName = instrumentationName;
    this.namespace = namespace;
    this.group = group;
  }

  public InstrumentationEntity(
      String srcPath,
      String instrumentationName,
      String namespace,
      String group,
      Map<InstrumentationType, Set<String>> targetVersions,
      InstrumentationMetaData metadata,
      InstrumentationScopeInfo scope) {
    this.srcPath = srcPath;
    this.instrumentationName = instrumentationName;
    this.namespace = namespace;
    this.group = group;
    this.targetVersions = targetVersions;
    this.metadata = metadata;
    this.scope = scope;
  }

  public void setMetadata(InstrumentationMetaData metadata) {
    this.metadata = metadata;
  }

  public InstrumentationMetaData getMetadata() {
    return metadata;
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

  public Map<InstrumentationType, Set<String>> getTargetVersions() {
    return targetVersions;
  }

  public void setTargetVersions(Map<InstrumentationType, Set<String>> targetVersions) {
    this.targetVersions = targetVersions;
  }

  public InstrumentationScopeInfo getScope() {
    return scope;
  }

  public void setScope(InstrumentationScopeInfo scope) {
    this.scope = scope;
  }
}
