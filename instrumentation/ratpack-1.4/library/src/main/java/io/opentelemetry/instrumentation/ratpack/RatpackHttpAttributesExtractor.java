/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.server.PublicAddress;

final class RatpackHttpAttributesExtractor
    extends HttpServerAttributesExtractor<Request, Response> {
  @Override
  protected String method(Request request) {
    return request.getMethod().getName();
  }

  @Override
  @Nullable
  protected String url(Request request) {
    // TODO(anuraaga): We should probably just not fill this
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/3700
    Context ratpackContext = request.get(Context.class);
    if (ratpackContext == null) {
      return null;
    }
    PublicAddress publicAddress = ratpackContext.get(PublicAddress.class);
    if (publicAddress == null) {
      return null;
    }
    return publicAddress
        .builder()
        .path(request.getPath())
        .params(request.getQueryParams())
        .build()
        .toString();
  }

  @Override
  protected String target(Request request) {
    // Uri is the path + query string, not a full URL
    return request.getUri();
  }

  @Override
  @Nullable
  protected String host(Request request) {
    return null;
  }

  @Override
  @Nullable
  protected String route(Request request) {
    // Ratpack route not available at the beginning of request.
    return null;
  }

  @Override
  @Nullable
  protected String scheme(Request request) {
    return null;
  }

  @Override
  @Nullable
  protected String userAgent(Request request) {
    return request.getHeaders().get("user-agent");
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
  @Nullable
  protected String flavor(Request request, @Nullable Response response) {
    switch (request.getProtocol()) {
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
  @Nullable
  protected String serverName(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  protected Integer statusCode(Request request, Response response) {
    return response.getStatus().getCode();
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
}
