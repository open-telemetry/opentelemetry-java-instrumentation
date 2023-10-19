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
  public String getHttpRequestMethod(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestMethod(requestContext.request());
  }

  @Override
  @Nullable
  public String getUrlScheme(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestScheme(requestContext.request());
  }

  @Nullable
  @Override
  public String getUrlPath(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestUri(requestContext.request());
  }

  @Nullable
  @Override
  public String getUrlQuery(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestQueryString(requestContext.request());
  }

  @Override
  public List<String> getHttpRequestHeader(
      ServletRequestContext<REQUEST> requestContext, String name) {
    return accessor.getRequestHeaderValues(requestContext.request(), name);
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext,
      @Nullable Throwable error) {
    RESPONSE response = responseContext.response();

    // OpenLiberty might call the AsyncListener with an AsyncEvent that does not contain a response
    // in some cases where the connection is dropped
    if (response == null) {
      return null;
    }

    if (!accessor.isResponseCommitted(response) && error != null) {
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
  public List<String> getHttpResponseHeader(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext,
      String name) {
    return accessor.getResponseHeaderValues(responseContext.response(), name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    String protocol = accessor.getRequestProtocol(requestContext.request());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    String protocol = accessor.getRequestProtocol(requestContext.request());
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerName(requestContext.request());
  }

  @Nullable
  @Override
  public Integer getServerPort(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestServerPort(requestContext.request());
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestRemoteAddr(requestContext.request());
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestRemotePort(requestContext.request());
  }

  @Nullable
  @Override
  public String getNetworkLocalAddress(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestLocalAddr(requestContext.request());
  }

  @Nullable
  @Override
  public Integer getNetworkLocalPort(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> response) {
    return accessor.getRequestLocalPort(requestContext.request());
  }
}
