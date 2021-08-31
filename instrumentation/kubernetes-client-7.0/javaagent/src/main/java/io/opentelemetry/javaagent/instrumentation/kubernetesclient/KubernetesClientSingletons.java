/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;
import static io.opentelemetry.javaagent.instrumentation.kubernetesclient.RequestBuilderInjectAdapter.SETTER;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import okhttp3.Request;

public class KubernetesClientSingletons {

  private static final Instrumenter<Request, ApiResponse<?>> INSTRUMENTER;
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBoolean("otel.instrumentation.kubernetes-client.experimental-span-attributes", false);
  private static final ContextPropagators CONTEXT_PROPAGATORS;

  static {
    KubernetesHttpAttributesExtractor httpAttributesExtractor =
        new KubernetesHttpAttributesExtractor();
    SpanNameExtractor<Request> spanNameExtractor =
        request -> KubernetesRequestDigest.parse(request).toString();

    INSTRUMENTER =
        Instrumenter.<Request, ApiResponse<?>>newBuilder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.kubernetes-client-7.0",
                spanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(new KubernetesNetAttributesExtractor())
            .addAttributesExtractor(new KubernetesExperimentalAttributesExtractor())
            .newInstrumenter(alwaysClient());

    CONTEXT_PROPAGATORS = GlobalOpenTelemetry.getPropagators();
  }

  public static Instrumenter<Request, ApiResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static void inject(Context context, Request.Builder requestBuilder) {
    CONTEXT_PROPAGATORS.getTextMapPropagator().inject(context, requestBuilder, SETTER);
  }

  public static boolean captureExperimentalSpanAttributes() {
    return CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;
  }

  private KubernetesClientSingletons() {}
}
