/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OpenTelemetryHttpClient extends HttpClient {
  private final HttpClient client;
  private final Instrumenter<HttpRequest, HttpResponse<?>> instrumenter;
  private final HttpHeadersSetter headersSetter;

  public OpenTelemetryHttpClient(
      HttpClient client,
      Instrumenter<HttpRequest, HttpResponse<?>> instrumenter,
      HttpHeadersSetter headersSetter) {
    this.client = client;
    this.instrumenter = instrumenter;
    this.headersSetter = headersSetter;
  }

  @Override
  public Optional<CookieHandler> cookieHandler() {
    return client.cookieHandler();
  }

  @Override
  public Optional<Duration> connectTimeout() {
    return client.connectTimeout();
  }

  @Override
  public Redirect followRedirects() {
    return client.followRedirects();
  }

  @Override
  public Optional<ProxySelector> proxy() {
    return client.proxy();
  }

  @Override
  public SSLContext sslContext() {
    return client.sslContext();
  }

  @Override
  public SSLParameters sslParameters() {
    return client.sslParameters();
  }

  @Override
  public Optional<Authenticator> authenticator() {
    return client.authenticator();
  }

  @Override
  public Version version() {
    return client.version();
  }

  @Override
  public Optional<Executor> executor() {
    return client.executor();
  }

  @Override
  public <T> HttpResponse<T> send(
      HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
      throws IOException, InterruptedException {
    Context parentContext = Context.current();
    if (request == null || !instrumenter.shouldStart(parentContext, request)) {
      return client.send(request, responseBodyHandler);
    }

    Context context = instrumenter.start(parentContext, request);
    HttpRequestWrapper requestWrapper =
        new HttpRequestWrapper(request, headersSetter.inject(request.headers(), context));
    HttpResponse<T> response;
    try (Scope ignore = context.makeCurrent()) {
      response = client.send(requestWrapper, responseBodyHandler);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }
    instrumenter.end(context, request, response, null);
    return response;
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
    return traceAsync(request, req -> client.sendAsync(req, responseBodyHandler));
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request,
      HttpResponse.BodyHandler<T> responseBodyHandler,
      HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
    return traceAsync(
        request, req -> client.sendAsync(req, responseBodyHandler, pushPromiseHandler));
  }

  private <T> CompletableFuture<HttpResponse<T>> traceAsync(
      HttpRequest request, Function<HttpRequest, CompletableFuture<HttpResponse<T>>> action) {
    Context parentContext = Context.current();
    if (request == null || !instrumenter.shouldStart(parentContext, request)) {
      return action.apply(request);
    }

    Context context = instrumenter.start(parentContext, request);
    HttpRequestWrapper requestWrapper =
        new HttpRequestWrapper(request, headersSetter.inject(request.headers(), context));

    CompletableFuture<HttpResponse<T>> future;
    try (Scope ignored = context.makeCurrent()) {
      future = action.apply(requestWrapper);
    } catch (Throwable t) {
      instrumenter.end(context, request, null, t);
      throw t;
    }
    future = future.whenComplete(new ResponseConsumer(instrumenter, context, request));
    future = CompletableFutureWrapper.wrap(future, parentContext);
    return future;
  }
}
