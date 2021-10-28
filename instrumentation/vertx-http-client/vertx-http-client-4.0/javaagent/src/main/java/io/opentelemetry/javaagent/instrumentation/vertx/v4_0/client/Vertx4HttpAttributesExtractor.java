/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import io.opentelemetry.javaagent.instrumentation.vertx.client.AbstractVertxHttpAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpVersion;
import javax.annotation.Nullable;

final class Vertx4HttpAttributesExtractor extends AbstractVertxHttpAttributesExtractor {

  @Override
  protected String url(HttpClientRequest request) {
    String uri = request.getURI();
    if (!isAbsolute(uri)) {
      uri = request.absoluteURI();
    }
    return uri;
  }

  private static boolean isAbsolute(String uri) {
    return uri.startsWith("http://") || uri.startsWith("https://");
  }

  @Override
  protected String method(HttpClientRequest request) {
    return request.getMethod().name();
  }

  @Nullable
  @Override
  protected String flavor(HttpClientRequest request, @Nullable HttpClientResponse response) {
    HttpVersion version = request.version();
    if (version == null) {
      return null;
    }
    switch (version) {
      case HTTP_1_0:
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case HTTP_1_1:
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
      case HTTP_2:
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    return null;
  }
}
