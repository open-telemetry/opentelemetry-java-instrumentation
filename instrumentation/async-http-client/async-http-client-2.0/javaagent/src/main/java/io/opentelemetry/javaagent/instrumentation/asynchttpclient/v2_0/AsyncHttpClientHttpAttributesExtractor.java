/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.asynchttpclient.Response;
import org.asynchttpclient.netty.request.NettyRequest;
import org.checkerframework.checker.nullness.qual.Nullable;

final class AsyncHttpClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<RequestContext, Response> {

  @Override
  protected String method(RequestContext requestContext) {
    return requestContext.getRequest().getMethod();
  }

  @Override
  protected String url(RequestContext requestContext) {
    return requestContext.getRequest().getUri().toUrl();
  }

  @Override
  @Nullable
  protected String userAgent(RequestContext requestContext) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLength(RequestContext requestContext, @Nullable Response response) {
    NettyRequest nettyRequest = requestContext.getNettyRequest();
    if (nettyRequest != null) {
      String contentLength = nettyRequest.getHttpRequest().headers().get("Content-Length");
      if (contentLength != null) {
        try {
          return Long.valueOf(contentLength);
        } catch (NumberFormatException ignored) {
          // ignore
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      RequestContext requestContext, @Nullable Response response) {
    return null;
  }

  @Override
  protected Integer statusCode(RequestContext requestContext, Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String flavor(RequestContext requestContext, @Nullable Response response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  @Nullable
  protected Long responseContentLength(RequestContext requestContext, Response response) {
    String contentLength = response.getHeaders().get("Content-Length");
    if (contentLength != null) {
      try {
        return Long.valueOf(contentLength);
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      RequestContext requestContext, Response response) {
    return null;
  }
}
