/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28;

public class StringTuple2 {
  public final String f1;
  public final String f2;

  private StringTuple2(String f1, String f2) {
    this.f1 = f1;
    this.f2 = f2;
  }

  public static StringTuple2 create(String f1, String f2) {
    return new StringTuple2(f1, f2);
  }
}
