/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientRequest;

final class FailedRequestWithUrlMaker {

  static HttpClientRequest create(HttpClientConfig config, HttpClientRequest failedRequest) {
    return (HttpClientRequest)
        Proxy.newProxyInstance(
            FailedRequestWithUrlMaker.class.getClassLoader(),
            new Class<?>[] {HttpClientRequest.class},
            new HttpRequestInvocationHandler(config, failedRequest));
  }

  private static final class HttpRequestInvocationHandler implements InvocationHandler {

    private final HttpClientConfig config;
    private final HttpClientRequest failedRequest;

    private HttpRequestInvocationHandler(HttpClientConfig config, HttpClientRequest failedRequest) {
      this.config = config;
      this.failedRequest = failedRequest;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("resourceUrl".equals(method.getName())) {
        return computeUrlFromConfig();
      }
      try {
        return method.invoke(failedRequest, args);
      } catch (InvocationTargetException exception) {
        throw exception.getCause();
      }
    }

    private String computeUrlFromConfig() {
      String uri = config.uri();
      if (isAbsolute(uri)) {
        return uri;
      }

      // use the baseUrl if it was configured
      String baseUrl = config.baseUrl();
      // baseUrl is an actual scheme+host+port base url, and not just "/"
      if (baseUrl != null && baseUrl.length() > 1) {
        if (baseUrl.endsWith("/")) {
          baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + uri;
      }

      // otherwise, use the host+port config to construct the full url
      SocketAddress hostAddress = config.remoteAddress().get();
      if (hostAddress instanceof InetSocketAddress) {
        InetSocketAddress inetHostAddress = (InetSocketAddress) hostAddress;
        return (config.isSecure() ? "https://" : "http://")
            + inetHostAddress.getHostString()
            + computePortPart(inetHostAddress.getPort())
            + uri;
      }

      return uri;
    }

    private static boolean isAbsolute(String uri) {
      return uri != null && !uri.isEmpty() && !uri.startsWith("/");
    }

    private String computePortPart(int port) {
      boolean defaultPortValue =
          (config.isSecure() && port == 443) || (!config.isSecure() && port == 80);
      return defaultPortValue ? "" : (":" + port);
    }
  }

  private FailedRequestWithUrlMaker() {}
}
