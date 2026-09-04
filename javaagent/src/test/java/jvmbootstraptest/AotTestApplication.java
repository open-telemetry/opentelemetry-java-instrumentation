/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jvmbootstraptest;

import io.opentracing.contrib.dropwizard.Trace;

public class AotTestApplication {

  public static void main(String[] args) {
    System.out.println(traced());
  }

  @Trace
  private static String traced() {
    return "AOT_INSTRUMENTATION_MARKER";
  }

  private AotTestApplication() {}
}
