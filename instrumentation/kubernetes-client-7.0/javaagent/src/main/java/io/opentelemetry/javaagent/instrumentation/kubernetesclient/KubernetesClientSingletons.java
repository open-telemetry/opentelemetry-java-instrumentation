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
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import okhttp3.Request;

public class KubernetesClientSingletons {

  private static final Instrumenter<Request, ApiResponse<?>> INSTRUMENTER;
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.kubernetes-client.experimental-span-attributes", false);
  private static final ContextPropagators CONTEXT_PROPAGATORS;

  static {
    INSTRUMENTER =
        DefaultHttpClientInstrumenterBuilder.create(
                "io.opentelemetry.kubernetes-client-7.0",
                GlobalOpenTelemetry.get(),
                new KubernetesHttpAttributesGetter())
            .configure(AgentCommonConfig.get())
            .setBuilderCustomizer(
                instrumenterBuilder -> {
                  if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
                    instrumenterBuilder.addAttributesExtractor(
                        new KubernetesExperimentalAttributesExtractor());
                  }
                })
            .setSpanNameExtractor(
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
