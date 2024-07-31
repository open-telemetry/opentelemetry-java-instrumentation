/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.instrumentation.api.internal.HttpProtocolUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpVersion;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum JettyClientHttpAttributesGetter
    implements HttpClientAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  @Nullable
  public String getHttpRequestMethod(Request request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  public String getUrlFull(Request request) {
    return request.getURI().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return request.getHeaders().getValuesList(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatus();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return response.getHeaders().getValuesList(name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    return "http";
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    HttpVersion httpVersion = null;
    if (response != null) {
      httpVersion = response.getVersion();
    }
    if (httpVersion == null) {
      httpVersion = request.getVersion();
    }
    if (httpVersion == null) {
      return null;
    }
    return HttpProtocolUtil.getVersion(httpVersion.toString());
  }

  @Override
  @Nullable
  public String getServerAddress(Request request) {
    return request.getHost();
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.getPort();
  }
}
