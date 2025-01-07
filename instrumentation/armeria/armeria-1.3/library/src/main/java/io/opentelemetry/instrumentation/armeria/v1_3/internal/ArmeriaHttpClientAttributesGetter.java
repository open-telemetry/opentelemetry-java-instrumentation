/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;

enum ArmeriaHttpClientAttributesGetter
    implements HttpClientAttributesGetter<ClientRequestContext, RequestLog> {
  INSTANCE;

  private static final ClassValue<Method> authorityMethodCache =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          try {
            return type.getMethod("authority");
          } catch (NoSuchMethodException e) {
            return null;
          }
        }
      };

  @Override
  public String getHttpRequestMethod(ClientRequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  public String getUrlFull(ClientRequestContext ctx) {
    HttpRequest request = request(ctx);
    StringBuilder uri = new StringBuilder();
    String scheme = request.scheme();
    if (scheme == null) {
      String name = ctx.sessionProtocol().uriText();
      if ("http".equals(name) || "https".equals(name)) {
        scheme = name;
      }
    }
    if (scheme != null) {
      uri.append(scheme).append("://");
    }
    String authority = authority(ctx);
    if (authority != null) {
      uri.append(authority);
    }
    uri.append(request.path());
    return uri.toString();
  }

  @Override
  public List<String> getHttpRequestHeader(ClientRequestContext ctx, String name) {
    return request(ctx).headers().getAll(name);
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      ClientRequestContext ctx, RequestLog requestLog, @Nullable Throwable error) {
    HttpStatus status = requestLog.responseHeaders().status();
    if (!status.equals(HttpStatus.UNKNOWN)) {
      return status.code();
    }
    return null;
  }

  @Override
  public List<String> getHttpResponseHeader(
      ClientRequestContext ctx, RequestLog requestLog, String name) {
    return requestLog.responseHeaders().getAll(name);
  }

  @Override
  public String getNetworkProtocolName(ClientRequestContext ctx, @Nullable RequestLog requestLog) {
    return "http";
  }

  @Override
  public String getNetworkProtocolVersion(
      ClientRequestContext ctx, @Nullable RequestLog requestLog) {
    SessionProtocol protocol =
        requestLog != null ? requestLog.sessionProtocol() : ctx.sessionProtocol();
    return protocol.isMultiplex() ? "2" : "1.1";
  }

  @Nullable
  @Override
  public String getServerAddress(ClientRequestContext ctx) {
    String authority = authority(ctx);
    if (authority == null) {
      return null;
    }
    int separatorPos = authority.indexOf(':');
    return separatorPos == -1 ? authority : authority.substring(0, separatorPos);
  }

  @Nullable
  @Override
  public Integer getServerPort(ClientRequestContext ctx) {
    String authority = authority(ctx);
    if (authority == null) {
      return null;
    }
    int separatorPos = authority.indexOf(':');
    if (separatorPos == -1) {
      return null;
    }
    try {
      return Integer.parseInt(authority.substring(separatorPos + 1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ClientRequestContext ctx, @Nullable RequestLog requestLog) {
    return RequestContextAccess.remoteAddress(ctx);
  }

  @Nullable
  private static String authority(ClientRequestContext ctx) {
    // newer armeria versions expose authority through DefaultClientRequestContext#authority
    // we are using this method as it provides default values based on endpoint
    // in older versions armeria wraps the request, and we can get the same default values through
    // the request
    Method method = authorityMethodCache.get(ctx.getClass());
    if (method != null) {
      try {
        return (String) method.invoke(ctx);
      } catch (Exception e) {
        return null;
      }
    }

    HttpRequest request = request(ctx);
    return request.authority();
  }

  private static HttpRequest request(ClientRequestContext ctx) {
    HttpRequest request = ctx.request();
    if (request == null) {
      throw new IllegalStateException(
          "Context always has a request in decorators, this exception indicates a programming bug.");
    }
    return request;
  }
}
