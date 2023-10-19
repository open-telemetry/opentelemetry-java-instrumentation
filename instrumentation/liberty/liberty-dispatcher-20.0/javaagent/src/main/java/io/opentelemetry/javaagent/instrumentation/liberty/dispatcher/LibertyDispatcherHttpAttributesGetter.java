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
  public String getHttpRequestMethod(LibertyRequest libertyRequest) {
    return libertyRequest.getMethod();
  }

  @Override
  public List<String> getHttpRequestHeader(LibertyRequest libertyRequest, String name) {
    return libertyRequest.getHeaderValues(name);
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, @Nullable Throwable error) {
    return libertyResponse.getStatus();
  }

  @Override
  public List<String> getHttpResponseHeader(
      LibertyRequest libertyRequest, LibertyResponse libertyResponse, String name) {
    return libertyResponse.getHeaderValues(name);
  }

  @Override
  @Nullable
  public String getUrlScheme(LibertyRequest libertyRequest) {
    return libertyRequest.getScheme();
  }

  @Nullable
  @Override
  public String getUrlPath(LibertyRequest request) {
    return request.getRequestUri();
  }

  @Nullable
  @Override
  public String getUrlQuery(LibertyRequest request) {
    return request.getQueryString();
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      LibertyRequest request, @Nullable LibertyResponse libertyResponse) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      LibertyRequest request, @Nullable LibertyResponse libertyResponse) {
    String protocol = request.getProtocol();
    if (protocol != null && protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(LibertyRequest request) {
    return request.request().getURLHost();
  }

  @Override
  public Integer getServerPort(LibertyRequest request) {
    return request.request().getURLPort();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(LibertyRequest request, @Nullable LibertyResponse response) {
    return request.getClientSocketAddress();
  }

  @Override
  public Integer getNetworkPeerPort(LibertyRequest request, @Nullable LibertyResponse response) {
    return request.getClientSocketPort();
  }

  @Nullable
  @Override
  public String getNetworkLocalAddress(LibertyRequest request, @Nullable LibertyResponse response) {
    return request.getServerSocketAddress();
  }

  @Nullable
  @Override
  public Integer getNetworkLocalPort(LibertyRequest request, @Nullable LibertyResponse response) {
    return request.getServerSocketPort();
  }
}
