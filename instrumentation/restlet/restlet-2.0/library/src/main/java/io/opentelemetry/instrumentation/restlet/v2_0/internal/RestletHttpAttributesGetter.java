/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletHeadersGetter.getHeaders;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.util.Series;

enum RestletHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {
  INSTANCE;

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
    Series<?> headers = getHeaders(request);
    if (headers == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(headers.getValuesArray(name, true));
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    Series<?> headers = getHeaders(response);
    if (headers == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(headers.getValuesArray(name, true));
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
    return ServerCallAccess.getServerAddress(request);
  }
}
