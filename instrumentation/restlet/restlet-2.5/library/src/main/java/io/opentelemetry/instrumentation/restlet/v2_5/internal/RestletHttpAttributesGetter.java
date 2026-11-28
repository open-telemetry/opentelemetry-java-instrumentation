/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_5.internal;

import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.engine.adapter.HttpRequest;

final class RestletHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {

  @Override
  public String getHttpRequestMethod(Request request) {
    return request.getMethod().toString();
  }

  @Override
  @Nullable
  public String getUrlScheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Nullable
  @Override
  public String getUrlPath(Request request) {
    return request.getOriginalRef().getPath();
  }

  @Nullable
  @Override
  public String getUrlQuery(Request request) {
    return request.getOriginalRef().getQuery();
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return asList(request.getHeaders().getValuesArray(name, /* ignoreCase= */ true));
  }

  @Override
  public Collection<String> getHttpRequestHeaderNames(Request request) {
    return request.getHeaders().getNames();
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return asList(response.getHeaders().getValuesArray(name, /* ignoreCase= */ true));
  }

  @Override
  public Collection<String> getHttpResponseHeaderNames(Request request, Response response) {
    return response.getHeaders().getNames();
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    return request.getProtocol().getSchemeName();
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    return request.getProtocol().getVersion();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(Request request, @Nullable Response response) {
    return request.getClientInfo().getAddress();
  }

  @Override
  public Integer getNetworkPeerPort(Request request, @Nullable Response response) {
    return request.getClientInfo().getPort();
  }

  @Nullable
  @Override
  public String getNetworkLocalAddress(Request request, @Nullable Response response) {
    return request instanceof HttpRequest
        ? ((HttpRequest) request).getHttpCall().getServerAddress()
        : null;
  }
}
