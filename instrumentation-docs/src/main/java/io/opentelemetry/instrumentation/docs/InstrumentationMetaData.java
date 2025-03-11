/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

public class InstrumentationMetaData {

  public InstrumentationMetaData() {}

  public InstrumentationMetaData(String description) {
    this.description = description;
  }

  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
