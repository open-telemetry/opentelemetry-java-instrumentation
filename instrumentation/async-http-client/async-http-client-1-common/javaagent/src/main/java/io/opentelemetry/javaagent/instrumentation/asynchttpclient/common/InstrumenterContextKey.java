/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class InstrumenterContextKey {
  public static final ContextKey<Instrumenter<?, ?>> KEY = 
      ContextKey.named("async-http-client-instrumenter");

  private InstrumenterContextKey() {}
}
