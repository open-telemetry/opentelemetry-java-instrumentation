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
  public String getMethod(LibertyRequest libertyRequest) {
    return libertyRequest.getMethod();
  }

  @Override
  public List<String> getRequestHeader(LibertyRequest libertyRequest, String name) {
    return libertyRequest.getHeaderValues(name);
  }

  @Override
  @Nullable
  public String getFlavor(LibertyRequest libertyRequest) {
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
  public Integer getStatusCode(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, @Nullable Throwable error) {
    return libertyResponse.getStatus();
  }

  @Override
  public List<String> getResponseHeader(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, String name) {
    return libertyResponse.getHeaderValues(name);
  }

  @Override
  @Nullable
  public String getTarget(LibertyRequest libertyRequest) {
    String requestUri = libertyRequest.getRequestUri();
    String queryString = libertyRequest.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      return requestUri + "?" + queryString;
    }
    return requestUri;
  }

  @Override
  @Nullable
  public String getScheme(LibertyRequest libertyRequest) {
    return libertyRequest.getScheme();
  }
}
