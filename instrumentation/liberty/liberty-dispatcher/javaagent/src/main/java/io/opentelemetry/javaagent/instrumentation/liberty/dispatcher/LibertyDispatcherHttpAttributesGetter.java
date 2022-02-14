/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

public class LibertyDispatcherHttpAttributesGetter
    implements HttpServerAttributesGetter<LibertyRequest, LibertyResponse> {

  @Override
  @Nullable
  public String method(LibertyRequest libertyRequest) {
    return libertyRequest.getMethod();
  }

  @Override
  public List<String> requestHeader(LibertyRequest libertyRequest, String name) {
    return libertyRequest.getHeaderValues(name);
  }

  @Override
  @Nullable
  public Long requestContentLength(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  @Nullable
  public String flavor(LibertyRequest libertyRequest) {
    String flavor = libertyRequest.getProtocol();
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
  public Integer statusCode(LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return libertyResponse.getStatus();
  }

  @Override
  @Nullable
  public Long responseContentLength(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, String name) {
    return libertyResponse.getHeaderValues(name);
  }

  @Override
  @Nullable
  public String target(LibertyRequest libertyRequest) {
    String requestUri = libertyRequest.getRequestUri();
    String queryString = libertyRequest.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      return requestUri + "?" + queryString;
    }
    return requestUri;
  }

  @Override
  @Nullable
  public String scheme(LibertyRequest libertyRequest) {
    return libertyRequest.getScheme();
  }

  @Override
  @Nullable
  public String route(LibertyRequest libertyRequest) {
    return null;
  }

  @Override
  @Nullable
  public String serverName(LibertyRequest libertyRequest) {
    return null;
  }
}
