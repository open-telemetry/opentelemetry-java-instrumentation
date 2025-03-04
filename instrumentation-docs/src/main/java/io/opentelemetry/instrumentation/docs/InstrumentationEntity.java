/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import java.util.List;

class InstrumentationEntity {
  private final String srcPath;
  private final String instrumentationName;
  private final String namespace;
  private final String group;
  private final List<InstrumentationType> types;
  private List<String> targetVersions;

  public InstrumentationEntity(
      String srcPath,
      String instrumentationName,
      String namespace,
      String group,
      List<InstrumentationType> types) {
    this.srcPath = srcPath;
    this.instrumentationName = instrumentationName;
    this.namespace = namespace;
    this.group = group;
    this.types = types;
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

  public List<InstrumentationType> getTypes() {
    return types;
  }

  public List<String> getTargetVersions() {
    return targetVersions;
  }

  public void setTargetVersions(List<String> targetVersions) {
    this.targetVersions = targetVersions;
  }
}
