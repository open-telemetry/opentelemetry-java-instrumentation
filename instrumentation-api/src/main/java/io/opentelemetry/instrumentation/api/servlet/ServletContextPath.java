/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

public class ServletContextPath {

  // Keeps track of the servlet context path that needs to be prepended to the route when updating
  // the span name
  public static final ContextKey<String> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-context-path-key");

  /** Returns the servlet context path from the given context or <code>""</code> if not found. */
  public static String prepend(Context context, String spanName) {
    String value = context.get(CONTEXT_KEY);
    // checking isEmpty just to avoid unnecessary string concat / allocation
    if (value != null && !value.isEmpty()) {
      return value + spanName;
    } else {
      return spanName;
    }
  }
}
