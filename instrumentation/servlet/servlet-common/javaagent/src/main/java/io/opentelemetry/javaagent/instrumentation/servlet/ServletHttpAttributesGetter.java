/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

public class ServletHttpAttributesGetter<REQUEST, RESPONSE>
    implements HttpServerAttributesGetter<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  protected final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletHttpAttributesGetter(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  @Nullable
  public String method(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestMethod(requestContext.request());
  }

  @Override
  @Nullable
  public String target(ServletRequestContext<REQUEST> requestContext) {
    REQUEST request = requestContext.request();
    String target = accessor.getRequestUri(request);
    String queryString = accessor.getRequestQueryString(request);
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  @Nullable
  public String scheme(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestScheme(requestContext.request());
  }

  @Override
  public List<String> requestHeader(ServletRequestContext<REQUEST> requestContext, String name) {
    return accessor.getRequestHeaderValues(requestContext.request(), name);
  }

  @Override
  @Nullable
  public Long requestContentLength(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    int contentLength = accessor.getRequestContentLength(requestContext.request());
    if (contentLength > -1) {
      return (long) contentLength;
    }
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    return null;
  }

  @Override
  @Nullable
  public String flavor(ServletRequestContext<REQUEST> requestContext) {
    String flavor = accessor.getRequestProtocol(requestContext.request());
    if (flavor != null) {
      // remove HTTP/ prefix to comply with semantic conventions
      if (flavor.startsWith("HTTP/")) {
        flavor = flavor.substring("HTTP/".length());
      }
    }
    return flavor;
  }

  @Override
  @Nullable
  public Integer statusCode(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    RESPONSE response = responseContext.response();

    if (!accessor.isResponseCommitted(response) && responseContext.error() != null) {
      // if response is not committed and there is a throwable set status to 500 /
      // INTERNAL_SERVER_ERROR, due to servlet spec
      // https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf:
      // "If a servlet generates an error that is not handled by the error page mechanism as
      // described above, the container must ensure to send a response with status 500."
      return 500;
    }
    return accessor.getResponseStatus(response);
  }

  @Override
  @Nullable
  public Long responseContentLength(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    String contentLength = accessor.getResponseHeader(responseContext.response(), "Content-Length");
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
  public Long responseContentLengthUncompressed(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext,
      String name) {
    return accessor.getResponseHeaderValues(responseContext.response(), name);
  }

  @Override
  @Nullable
  public String route(ServletRequestContext<REQUEST> requestContext) {
    return null;
  }

  @Override
  @Nullable
  public String serverName(ServletRequestContext<REQUEST> requestContext) {
    return null;
  }
}
