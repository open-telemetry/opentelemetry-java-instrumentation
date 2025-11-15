/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

public enum ElasticJobType {
  SIMPLE("SIMPLE"),
  DATAFLOW("DATAFLOW"),
  HTTP("HTTP"),
  SCRIPT("SCRIPT"),
  UNKNOWN("UNKNOWN");

  private final String value;

  ElasticJobType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
