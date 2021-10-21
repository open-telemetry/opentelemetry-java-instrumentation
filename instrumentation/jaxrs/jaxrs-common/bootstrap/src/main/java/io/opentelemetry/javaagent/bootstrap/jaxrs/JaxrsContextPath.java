/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.jaxrs;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

/**
 * Helper container for storing context path for jax-rs requests. Jax-rs context path is the path
 * where jax-rs servlet is mapped or the value of ApplicationPath annotation. Span name is built by
 * combining servlet context path from {@code
 * io.opentelemetry.instrumentation.api.servlet.ServletContextPath}, jax-rs context path and the
 * Path annotation from called method or class.
 */
public final class JaxrsContextPath {
  private static final ContextKey<String> CONTEXT_KEY =
      ContextKey.named("opentelemetry-jaxrs-context-path-key");

  private JaxrsContextPath() {}

  @Nullable
  public static Context init(Context context, String path) {
    if (path == null || path.isEmpty() || "/".equals(path)) {
      return null;
    }
    // normalize path to have a leading slash and no trailing slash
    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return context.with(CONTEXT_KEY, path);
  }

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
