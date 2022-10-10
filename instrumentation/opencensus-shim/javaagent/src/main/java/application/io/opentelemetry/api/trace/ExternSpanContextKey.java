/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package application.io.opentelemetry.api.trace;

import application.io.opentelemetry.context.ContextKey;

public class ExternSpanContextKey {
  public static final ContextKey<Span> KEY = SpanContextKey.KEY;

  private ExternSpanContextKey() {}
}
