/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.v0_8;

@SuppressWarnings("unused")
public class ClickHouseClientV2ExampleData {
  private String value;

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public ClickHouseClientV2ExampleData(String value) {
    this.value = value;
  }
}
