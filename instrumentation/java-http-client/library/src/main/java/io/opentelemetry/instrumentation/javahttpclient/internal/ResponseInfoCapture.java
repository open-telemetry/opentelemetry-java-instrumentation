/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ResponseInfoCapture {

  @Nullable private volatile HttpResponse.ResponseInfo responseInfo;

  public <T> HttpResponse.BodyHandler<T> wrap(HttpResponse.BodyHandler<T> bodyHandler) {
    return info -> {
      responseInfo = info;
      return bodyHandler.apply(info);
    };
  }

  @Nullable
  public HttpResponse<?> response(HttpRequest request, Throwable error) {
    if (!isIoException(error)) {
      return null;
    }
    HttpResponse.ResponseInfo info = responseInfo;
    return info == null ? null : new CapturedResponse(request, info);
  }

  private static boolean isIoException(Throwable error) {
    Throwable current = error;
    boolean ioException = false;
    while (current != null) {
      if (current instanceof CancellationException) {
        return false;
      }
      if (current instanceof IOException) {
        ioException = true;
      }
      current = current.getCause();
    }
    return ioException;
  }

  private static final class CapturedResponse implements HttpResponse<Object> {

    private final HttpRequest request;
    private final HttpResponse.ResponseInfo responseInfo;

    private CapturedResponse(HttpRequest request, HttpResponse.ResponseInfo responseInfo) {
      this.request = request;
      this.responseInfo = responseInfo;
    }

    @Override
    public int statusCode() {
      return responseInfo.statusCode();
    }

    @Override
    public HttpRequest request() {
      return request;
    }

    @Override
    public Optional<HttpResponse<Object>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return responseInfo.headers();
    }

    @Nullable
    @Override
    public Object body() {
      return null;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request.uri();
    }

    @Override
    public HttpClient.Version version() {
      return responseInfo.version();
    }
  }
}
