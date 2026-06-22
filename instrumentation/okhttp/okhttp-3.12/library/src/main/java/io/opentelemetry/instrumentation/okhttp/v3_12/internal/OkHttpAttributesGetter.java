/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12.internal;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("OtelDeprecatedApiUsage") // SPDY_3 is deprecated but still used in okhttp 3.x
public final class OkHttpAttributesGetter implements HttpClientAttributesGetter<Call, Response> {

  @Override
  public String getHttpRequestMethod(Call call) {
    return call.request().method();
  }

  @Override
  public String getUrlFull(Call call) {
    return call.request().url().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(Call call, String name) {
    return call.request().headers(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Call call, Response response, @Nullable Throwable error) {
    return response.code();
  }

  @Override
  public List<String> getHttpResponseHeader(Call call, Response response, String name) {
    return response.headers(name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Call call, @Nullable Response response) {
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
      default:
        if ("H2_PRIOR_KNOWLEDGE".equals(response.protocol().name())) {
          return "http";
        }
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Call call, @Nullable Response response) {
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
      default:
        if ("H2_PRIOR_KNOWLEDGE".equals(response.protocol().name())) {
          return "2";
        }
    }
    return null;
  }

  @Override
  @Nullable
  public String getServerAddress(Call call) {
    return call.request().url().host();
  }

  @Override
  public Integer getServerPort(Call call) {
    return call.request().url().port();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(Call call, @Nullable Response response) {
    OkHttpClientCallState state = OkHttpClientCallState.get(call);
    Connection connection = state != null ? state.connection() : null;
    if (connection == null) {
      return null;
    }
    SocketAddress socketAddress = connection.socket().getRemoteSocketAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) socketAddress;
    }
    return null;
  }
}
