/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;

public final class HttpUrlConnectionSingletons {

  public static final VirtualField<HttpURLConnection, HttpUrlState> HTTP_URL_STATE =
      VirtualField.find(HttpURLConnection.class, HttpUrlState.class);

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
                        HttpMethodAttributeExtractor.create(getKnownHttpMethods()))
                    .addContextCustomizer(
                        (context, httpRequestPacket, startAttributes) ->
                            GetOutputStreamContext.init(context)));
  }

  public static Instrumenter<HttpURLConnection, Integer> instrumenter() {
    return INSTRUMENTER;
  }

  private static Set<String> getKnownHttpMethods() {
    return DeclarativeConfigUtil.getList(
            GlobalOpenTelemetry.get(), "general", "http", "known_methods")
        .map(HashSet::new)
        .orElse(HttpConstants.KNOWN_METHODS);
  }

  private HttpUrlConnectionSingletons() {}
}
