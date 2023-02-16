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
  public String getMethod(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestMethod(requestContext.request());
  }

  @Override
  @Nullable
  public String getTarget(ServletRequestContext<REQUEST> requestContext) {
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
  public String getScheme(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestScheme(requestContext.request());
  }

  @Override
  public List<String> getRequestHeader(ServletRequestContext<REQUEST> requestContext, String name) {
    return accessor.getRequestHeaderValues(requestContext.request(), name);
  }

  @Override
  @Nullable
  public String getFlavor(ServletRequestContext<REQUEST> requestContext) {
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
  public Integer getStatusCode(
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
  public List<String> getResponseHeader(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext,
      String name) {
    return accessor.getResponseHeaderValues(responseContext.response(), name);
  }
}
