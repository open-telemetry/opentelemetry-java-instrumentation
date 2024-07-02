/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import java.net.HttpURLConnection;

public final class HttpUrlConnectionSingletons {

  private static final Instrumenter<HttpURLConnection, Integer> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpClientInstrumenters.create(
            "io.opentelemetry.http-url-connection",
            new HttpUrlHttpAttributesGetter(),
            RequestPropertySetter.INSTANCE,
            builder ->
                builder
                    .addAttributesExtractor(
                        HttpMethodAttributeExtractor.create(
                            AgentCommonConfig.get().getKnownHttpRequestMethods()))
                    .addContextCustomizer(
                        (context, httpRequestPacket, startAttributes) ->
                            GetOutputStreamContext.init(context)));
  }

  public static Instrumenter<HttpURLConnection, Integer> instrumenter() {
    return INSTRUMENTER;
  }

  private HttpUrlConnectionSingletons() {}
}
