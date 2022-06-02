/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs;

public final class JaxrsConstants {
  public static final String ABORT_FILTER_CLASS =
      "io.opentelemetry.javaagent.instrumentation.jaxrs.filter.abort.class";
  public static final String ABORT_HANDLED =
      "io.opentelemetry.javaagent.instrumentation.jaxrs.filter.abort.handled";

  private JaxrsConstants() {}
}
