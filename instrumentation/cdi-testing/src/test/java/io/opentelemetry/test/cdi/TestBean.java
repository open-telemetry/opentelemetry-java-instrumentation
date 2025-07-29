/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.cdi;

public class TestBean {

  private String someField;

  public String getSomeField() {
    return someField;
  }

  public void setSomeField(String someField) {
    this.someField = someField;
  }
}
