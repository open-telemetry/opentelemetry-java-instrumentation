/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.access.jetty.v12_0;

public class LogbackAccessSingletons {
  private static final AccessEventMapper mapper;

  static {
    mapper = new AccessEventMapper();
  }

  public static AccessEventMapper mapper() {
    return mapper;
  }

  private LogbackAccessSingletons() {}
}
