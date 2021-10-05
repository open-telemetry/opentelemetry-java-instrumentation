/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LibertyDispatcherHttpAttributesExtractor
    extends HttpServerAttributesExtractor<LibertyRequest, LibertyResponse> {

  @Override
  protected @Nullable String method(LibertyRequest libertyRequest) {
    return libertyRequest.getMethod();
  }

  @Override
  protected @Nullable String userAgent(LibertyRequest libertyRequest) {
    return libertyRequest.getHeaderValue("User-Agent");
  }

  @Override
  protected List<String> requestHeader(LibertyRequest libertyRequest, String name) {
    return libertyRequest.getHeaderValues(name);
  }

  @Override
  protected @Nullable Long requestContentLength(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  protected @Nullable String flavor(LibertyRequest libertyRequest) {
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
  protected @Nullable Integer statusCode(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return libertyResponse.getStatus();
  }

  @Override
  protected @Nullable Long responseContentLength(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, String name) {
    return libertyResponse.getHeaderValues(name);
  }

  @Override
  protected @Nullable String target(LibertyRequest libertyRequest) {
    String requestUri = libertyRequest.getRequestUri();
    String queryString = libertyRequest.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      return requestUri + "?" + queryString;
    }
    return requestUri;
  }

  @Override
  protected String host(LibertyRequest libertyRequest) {
    return libertyRequest.getServerName() + ":" + libertyRequest.getServerPort();
  }

  @Override
  protected @Nullable String scheme(LibertyRequest libertyRequest) {
    return libertyRequest.getScheme();
  }

  @Override
  protected @Nullable String route(LibertyRequest libertyRequest) {
    return null;
  }

  @Override
  protected @Nullable String serverName(
      LibertyRequest libertyRequest, @Nullable LibertyResponse libertyResponse) {
    return null;
  }
}
