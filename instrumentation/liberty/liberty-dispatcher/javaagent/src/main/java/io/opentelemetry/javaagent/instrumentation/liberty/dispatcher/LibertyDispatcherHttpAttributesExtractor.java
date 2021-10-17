/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;

public class LibertyDispatcherHttpAttributesExtractor
    extends HttpServerAttributesExtractor<LibertyRequest, LibertyResponse> {

  @Override
  @Nullable
  protected String method(LibertyRequest libertyRequest) {
    return libertyRequest.getMethod();
  }

  @Override
  protected List<String> requestHeader(LibertyRequest libertyRequest, String name) {
    return libertyRequest.getHeaderValues(name);
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  @Nullable
  protected String flavor(LibertyRequest libertyRequest) {
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
  protected Integer statusCode(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return libertyResponse.getStatus();
  }

  @Override
  @Nullable
  protected Long responseContentLength(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, String name) {
    return libertyResponse.getHeaderValues(name);
  }

  @Override
  @Nullable
  protected String target(LibertyRequest libertyRequest) {
    String requestUri = libertyRequest.getRequestUri();
    String queryString = libertyRequest.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      return requestUri + "?" + queryString;
    }
    return requestUri;
  }

  @Override
  @Nullable
  protected String scheme(LibertyRequest libertyRequest) {
    return libertyRequest.getScheme();
  }

  @Override
  @Nullable
  protected String route(LibertyRequest libertyRequest) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }
}
