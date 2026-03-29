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

  private static final Instrumenter<Request, Response> INSTRUMENTER;
  private static final TracingInterceptor TRACING_INTERCEPTOR;

  public static final VirtualField<Runnable, PropagatedContext> PROPAGATED_CONTEXT =
      VirtualField.find(Runnable.class, PropagatedContext.class);

  static {
    INSTRUMENTER =
        JavaagentHttpClientInstrumenters.create(
            INSTRUMENTATION_NAME, new OkHttp2HttpAttributesGetter());

    TRACING_INTERCEPTOR =
        new TracingInterceptor(INSTRUMENTER, GlobalOpenTelemetry.get().getPropagators());
  }

  public static Interceptor tracingInterceptor() {
    return TRACING_INTERCEPTOR;
  }

  private OkHttp2Singletons() {}
}
