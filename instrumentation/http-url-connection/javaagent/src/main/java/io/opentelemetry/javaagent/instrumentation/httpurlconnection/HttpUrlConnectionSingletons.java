/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenterBuilder;
import java.net.HttpURLConnection;
import java.util.Optional;

public final class HttpUrlConnectionSingletons {

  private static final Instrumenter<HttpURLConnection, Integer> INSTRUMENTER;

  static {
    INSTRUMENTER =
        JavaagentHttpClientInstrumenterBuilder.createWithCustomizer(
            "io.opentelemetry.http-url-connection",
            new HttpUrlHttpAttributesGetter(),
            Optional.of(RequestPropertySetter.INSTANCE),
            builder ->
                builder
                    .addAttributesExtractor(
                        HttpMethodAttributeExtractor.create(
                            CommonConfig.get().getKnownHttpRequestMethods()))
                    .addContextCustomizer(
                        (context, httpRequestPacket, startAttributes) ->
                            GetOutputStreamContext.init(context)));
  }

  public static Instrumenter<HttpURLConnection, Integer> instrumenter() {
    return INSTRUMENTER;
  }

  private HttpUrlConnectionSingletons() {}
}
