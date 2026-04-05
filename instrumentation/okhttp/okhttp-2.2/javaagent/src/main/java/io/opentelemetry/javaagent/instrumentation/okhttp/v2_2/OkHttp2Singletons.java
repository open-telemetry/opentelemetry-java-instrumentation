/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;

public final class OkHttp2Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-2.2";

  private static final Instrumenter<Request, Response> instrumenter;
  private static final TracingInterceptor tracingInterceptor;

  public static final VirtualField<Runnable, PropagatedContext> PROPAGATED_CONTEXT =
      VirtualField.find(Runnable.class, PropagatedContext.class);

  static {
    instrumenter =
        JavaagentHttpClientInstrumenters.create(
            INSTRUMENTATION_NAME, new OkHttp2HttpAttributesGetter());

    tracingInterceptor =
        new TracingInterceptor(instrumenter, GlobalOpenTelemetry.get().getPropagators());
  }

  public static Interceptor tracingInterceptor() {
    return tracingInterceptor;
  }

  private OkHttp2Singletons() {}
}
