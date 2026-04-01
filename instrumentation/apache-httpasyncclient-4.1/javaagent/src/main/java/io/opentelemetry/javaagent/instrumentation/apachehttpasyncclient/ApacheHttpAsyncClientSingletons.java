/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import org.apache.http.HttpResponse;

public final class ApacheHttpAsyncClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpasyncclient-4.1";

  private static final Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter;

  static {
    instrumenter =
        JavaagentHttpClientInstrumenters.create(
            INSTRUMENTATION_NAME,
            new ApacheHttpAsyncClientHttpAttributesGetter(),
            new HttpHeaderSetter());
  }

  public static Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  private ApacheHttpAsyncClientSingletons() {}
}
