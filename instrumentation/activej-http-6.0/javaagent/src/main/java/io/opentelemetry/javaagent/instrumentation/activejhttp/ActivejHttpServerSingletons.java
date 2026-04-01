/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;

public final class ActivejHttpServerSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.activej-http-6.0";

  private static final Instrumenter<HttpRequest, HttpResponse> instrumenter;

  static {
    instrumenter =
        JavaagentHttpServerInstrumenters.create(
            INSTRUMENTATION_NAME,
            new ActivejHttpServerAttributesGetter(),
            new ActivejHttpServerRequestGetter());
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  private ActivejHttpServerSingletons() {}
}
