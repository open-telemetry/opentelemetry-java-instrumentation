/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.api.config.HttpHeadersConfig;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

final class PlayWsClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<Request, Response> {

  PlayWsClientHttpAttributesExtractor() {
    super(HttpHeadersConfig.capturedClientHeaders());
  }

  @Override
  protected String method(Request request) {
    return request.getMethod();
  }

  @Override
  protected String url(Request request) {
    return request.getUri().toUrl();
  }

  @Override
  @Nullable
  protected String userAgent(Request request) {
    return null;
  }

  @Override
  protected List<String> requestHeader(Request request, String name) {
    return request.getHeaders().getAll(name);
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
  protected List<String> responseHeader(Request request, Response response, String name) {
    return response.getHeaders().getAll(name);
  }
}
