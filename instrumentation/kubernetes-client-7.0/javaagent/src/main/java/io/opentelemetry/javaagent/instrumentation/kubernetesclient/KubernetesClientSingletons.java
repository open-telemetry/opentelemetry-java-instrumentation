/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import okhttp3.Request;

public class KubernetesClientSingletons {

  private static final Instrumenter<Request, ApiResponse<?>> INSTRUMENTER;
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.kubernetes-client.experimental-span-attributes", false);
  private static final ContextPropagators CONTEXT_PROPAGATORS;

  static {
    KubernetesHttpAttributesGetter httpAttributesGetter = new KubernetesHttpAttributesGetter();

    InstrumenterBuilder<Request, ApiResponse<?>> instrumenterBuilder =
        Instrumenter.<Request, ApiResponse<?>>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.kubernetes-client-7.0",
                request -> KubernetesRequestDigest.parse(request).toString())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build());

    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      instrumenterBuilder.addAttributesExtractor(new KubernetesExperimentalAttributesExtractor());
    }

    // Initialize with .newInstrumenter(alwaysClient()) instead of .newClientInstrumenter(..)
    // because Request is immutable so context must be injected manually
    INSTRUMENTER = instrumenterBuilder.buildInstrumenter(alwaysClient());

    CONTEXT_PROPAGATORS = GlobalOpenTelemetry.getPropagators();
  }

  public static Instrumenter<Request, ApiResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static void inject(Context context, Request.Builder requestBuilder) {
    CONTEXT_PROPAGATORS
        .getTextMapPropagator()
        .inject(context, requestBuilder, RequestBuilderHeaderSetter.INSTANCE);
  }

  private KubernetesClientSingletons() {}
}
