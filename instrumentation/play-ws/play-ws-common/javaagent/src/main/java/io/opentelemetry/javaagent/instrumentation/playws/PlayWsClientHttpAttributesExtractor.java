/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;
import play.shaded.ahc.org.asynchttpclient.uri.Uri;

final class PlayWsClientHttpAttributesExtractor extends HttpAttributesExtractor<Request, Response> {

  @Override
  protected String method(Request request) {
    return request.getMethod();
  }

  @Override
  protected String url(Request request) {
    return request.getUri().toUrl();
  }

  @Override
  protected String target(Request request) {
    Uri uri = request.getUri();
    String query = uri.getQuery();
    return query != null ? uri.getPath() + "?" + query : uri.getPath();
  }

  @Override
  @Nullable
  protected String host(Request request) {
    String host = request.getHeaders().get("Host");
    if (host != null) {
      return host;
    }
    return request.getVirtualHost();
  }

  @Override
  @Nullable
  protected String scheme(Request request) {
    return request.getUri().getScheme();
  }

  @Override
  @Nullable
  protected String userAgent(Request request) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected Integer statusCode(Request request, Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String flavor(Request request, @Nullable Response response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  @Nullable
  protected Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  protected String route(Request request) {
    return null;
  }
}
