/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import java.net.InetSocketAddress;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;

public class PekkoHttpServerSingletons {

  public static final VirtualField<HttpRequest, InetSocketAddress> HTTP_REQUEST_PEER_ADDRESS =
      VirtualField.find(HttpRequest.class, InetSocketAddress.class);

  private static final Instrumenter<HttpRequest, HttpResponse> instrumenter;

  static {
    instrumenter =
        JavaagentHttpServerInstrumenters.create(
            PekkoHttpUtil.instrumentationName(),
            new PekkoHttpServerAttributesGetter(),
            new PekkoHttpServerHeaders());
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return instrumenter;
  }

  public static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }

  private PekkoHttpServerSingletons() {}
}
