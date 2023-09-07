/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum OkHttpAttributesGetter implements HttpClientAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(Request request) {
    return request.method();
  }

  @Override
  public String getUrlFull(Request request) {
    return request.url().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return request.headers(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.code();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return response.headers(name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    switch (response.protocol()) {
      case HTTP_1_0:
      case HTTP_1_1:
      case HTTP_2:
        return "http";
      case SPDY_3:
        return "spdy";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    switch (response.protocol()) {
      case HTTP_1_0:
        return "1.0";
      case HTTP_1_1:
        return "1.1";
      case HTTP_2:
        return "2";
      case SPDY_3:
        return "3.1";
    }
    return null;
  }

  @Override
  @Nullable
  public String getServerAddress(Request request) {
    return request.url().host();
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.url().port();
  }
}
