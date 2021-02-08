/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.extension.annotations.WithSpan;
import java.util.concurrent.Callable;

/**
 * Some of our tests need to verify behavior when a span has been registered as the server span in
 * context. These tests only happen with the agent right now, so we can use WithSpan to have the
 * agent create a span under its management (where the context key is shaded). This testing approach
 * will not work for library instrumentation that may use these keys in the future. This can be
 * solved by https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1726
 */
public final class ServerTraceUtils {

  @WithSpan(kind = SpanKind.SERVER)
  public static <T> T runUnderServerTrace(String spanName, Callable<T> r) throws Exception {
    Span.current().updateName(spanName);
    return r.call();
  }

  private ServerTraceUtils() {}
}
