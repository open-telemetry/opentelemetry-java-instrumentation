/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.jakarta;

public class GreetingForm {

  private String name = "";
  private String message = "";

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getMessage() {
    return message;
  }

  public void submit() {
    message = "Hello " + name;
    if (name.equals("exception")) {
      throw new IllegalStateException("submit exception");
    }
  }
}
