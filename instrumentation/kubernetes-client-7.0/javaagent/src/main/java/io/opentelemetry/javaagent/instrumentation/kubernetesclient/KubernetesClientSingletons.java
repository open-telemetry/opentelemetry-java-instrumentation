/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import okhttp3.Request;

public class KubernetesClientSingletons {

  private static final Instrumenter<Request, ApiResponse<?>> INSTRUMENTER;
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(),
              "java",
              "kubernetes_client",
              "experimental_span_attributes")
          .orElse(false);
  private static final ContextPropagators CONTEXT_PROPAGATORS;

  static {
    INSTRUMENTER =
        DefaultHttpClientInstrumenterBuilder.create(
                "io.opentelemetry.kubernetes-client-7.0",
                GlobalOpenTelemetry.get(),
                new KubernetesHttpAttributesGetter())
            .configure(GlobalOpenTelemetry.get())
            .setBuilderCustomizer(
                instrumenterBuilder -> {
                  if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
                    instrumenterBuilder.addAttributesExtractor(
                        new KubernetesExperimentalAttributesExtractor());
                  }
                })
            .setSpanNameExtractorCustomizer(
                requestSpanNameExtractor ->
                    request -> KubernetesRequestDigest.parse(request).toString())
            .build();

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
