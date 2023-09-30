/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletHttpAttributesGetter;
import io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import java.util.Collections;
import org.restlet.Request;
import org.restlet.Response;

public final class RestletSingletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      RestletInstrumenterFactory.newServerInstrumenter(
          GlobalOpenTelemetry.get(),
          HttpServerAttributesExtractor.builder(RestletHttpAttributesGetter.INSTANCE)
              .setCapturedRequestHeaders(CommonConfig.get().getServerRequestHeaders())
              .setCapturedResponseHeaders(CommonConfig.get().getServerResponseHeaders())
              .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
              .build(),
          HttpSpanNameExtractor.builder(RestletHttpAttributesGetter.INSTANCE)
              .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
              .build(),
          HttpServerRoute.builder(RestletHttpAttributesGetter.INSTANCE)
              .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
              .build(),
          Collections.emptyList(),
          CommonConfig.get().shouldEmitExperimentalHttpServerMetrics());

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpServerRouteGetter<String> serverSpanName() {
    return ServletContextPath::prepend;
  }

  private RestletSingletons() {}
}
