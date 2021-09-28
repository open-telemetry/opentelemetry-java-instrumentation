/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.UriBuilder;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ServletHttpAttributesExtractor<REQUEST, RESPONSE>
    extends HttpServerAttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  protected final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletHttpAttributesExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  protected @Nullable String method(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestMethod(requestContext.request());
  }

  @Override
  protected @Nullable String url(ServletRequestContext<REQUEST> requestContext) {
    REQUEST request = requestContext.request();

    return UriBuilder.uri(
        accessor.getRequestScheme(request),
        accessor.getRequestServerName(request),
        accessor.getRequestServerPort(request),
        accessor.getRequestUri(request),
        accessor.getRequestQueryString(request));
  }

  @Override
  protected @Nullable String target(ServletRequestContext<REQUEST> requestContext) {
    /*
    String target = httpServletRequest.getRequestURI();
    String queryString = httpServletRequest.getQueryString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
     */
    return null;
  }

  @Override
  protected @Nullable String host(ServletRequestContext<REQUEST> requestContext) {
    /*
    REQUEST request = requestContext.request();
    return accessor.getRequestServerName(request) + ":" + accessor.getRequestServerPort(request);
     */
    return null;
  }

  @Override
  protected @Nullable String scheme(ServletRequestContext<REQUEST> requestContext) {
    // return accessor.getRequestScheme(requestContext.request());
    return null;
  }

  @Override
  protected @Nullable String userAgent(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestHeader(requestContext.request(), "User-Agent");
  }

  @Override
  protected @Nullable Long requestContentLength(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    /*
    int contentLength = accessor.getRequestContentLength(requestContext.request());
    if (contentLength > -1) {
      return (long) contentLength;
    }
     */
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    return null;
  }

  @Override
  protected @Nullable String flavor(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
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
  protected @Nullable Integer statusCode(
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
  protected @Nullable Long responseContentLength(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    /*
    String contentLength = servletAccessor.getResponseHeader(responseContext.response(), "Content-Length");
    if (contentLength != null) {
      try {
        return Long.valueOf(contentLength);
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
     */
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    return null;
  }

  @Override
  protected @Nullable String route(ServletRequestContext<REQUEST> requestContext) {
    return null;
  }

  @Override
  protected @Nullable String serverName(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    // return servletAccessor.getRequestServerName(requestContext.request());
    return null;
  }
}
