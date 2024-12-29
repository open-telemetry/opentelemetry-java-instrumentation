/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum OkHttpAttributesGetter
    implements HttpClientAttributesGetter<Interceptor.Chain, Response> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(Interceptor.Chain chain) {
    return chain.request().method();
  }

  @Override
  public String getUrlFull(Interceptor.Chain chain) {
    return chain.request().url().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(Interceptor.Chain chain, String name) {
    return chain.request().headers(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Interceptor.Chain chain, Response response, @Nullable Throwable error) {
    return response.code();
  }

  @Override
  public List<String> getHttpResponseHeader(
      Interceptor.Chain chain, Response response, String name) {
    return response.headers(name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Interceptor.Chain chain, @Nullable Response response) {
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
    // added in 3.11.0
    if ("H2_PRIOR_KNOWLEDGE".equals(response.protocol().name())) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Interceptor.Chain chain, @Nullable Response response) {
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
    // added in 3.11.0
    if ("H2_PRIOR_KNOWLEDGE".equals(response.protocol().name())) {
      return "2";
    }
    return null;
  }

  @Override
  @Nullable
  public String getServerAddress(Interceptor.Chain chain) {
    return chain.request().url().host();
  }

  @Override
  public Integer getServerPort(Interceptor.Chain chain) {
    return chain.request().url().port();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      Interceptor.Chain chain, @Nullable Response response) {
    Connection connection = chain.connection();
    if (connection == null) {
      return null;
    }
    SocketAddress socketAddress = connection.socket().getRemoteSocketAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) socketAddress;
    } else {
      return null;
    }
  }
}
