/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import org.asynchttpclient.Response;
import org.asynchttpclient.netty.request.NettyRequest;

final class AsyncHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<RequestContext, Response> {

  @Override
  public String method(RequestContext requestContext) {
    return requestContext.getRequest().getMethod();
  }

  @Override
  public String url(RequestContext requestContext) {
    return requestContext.getRequest().getUri().toUrl();
  }

  @Override
  public List<String> requestHeader(RequestContext requestContext, String name) {
    return requestContext.getRequest().getHeaders().getAll(name);
  }

  @Override
  @Nullable
  public Long requestContentLength(RequestContext requestContext, @Nullable Response response) {
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
  public Long requestContentLengthUncompressed(
      RequestContext requestContext, @Nullable Response response) {
    return null;
  }

  @Override
  public Integer statusCode(RequestContext requestContext, Response response) {
    return response.getStatusCode();
  }

  @Override
  public String flavor(RequestContext requestContext, @Nullable Response response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  @Nullable
  public Long responseContentLength(RequestContext requestContext, Response response) {
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
  public Long responseContentLengthUncompressed(RequestContext requestContext, Response response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      RequestContext requestContext, Response response, String name) {
    return response.getHeaders().getAll(name);
  }
}
