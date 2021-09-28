/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;

final class RestletHttpAttributesExtractor
    extends HttpServerAttributesExtractor<Request, Response> {

  @Override
  protected String method(Request request) {
    return request.getMethod().toString();
  }

  @Override
  protected String url(Request request) {
    return request.getOriginalRef().toString();
  }

  @Override
  protected @Nullable String target(Request request) {
    Reference ref = request.getOriginalRef();
    String path = ref.getPath();
    return ref.hasQuery() ? path + "?" + ref.getQuery() : path;
  }

  @Override
  protected @Nullable String host(Request request) {
    return null;
  }

  @Override
  protected @Nullable String route(Request request) {
    return null;
  }

  @Override
  protected @Nullable String scheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Override
  protected @Nullable String userAgent(Request request) {
    return request.getClientInfo().getAgent();
  }

  @Override
  protected @Nullable Long requestContentLength(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected @Nullable String flavor(Request request, @Nullable Response response) {
    String version = (String) request.getAttributes().get("org.restlet.http.version");
    switch (version) {
      case "HTTP/1.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case "HTTP/1.1":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
      case "HTTP/2.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
      default:
        // fall through
    }
    return null;
  }

  @Override
  protected @Nullable String serverName(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected Integer statusCode(Request request, Response response) {
    return response.getStatus().getCode();
  }

  @Override
  protected @Nullable Long responseContentLength(Request request, Response response) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(Request request, Response response) {
    return null;
  }
}
