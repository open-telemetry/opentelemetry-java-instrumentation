/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.playws.v2_1;

import static io.opentelemetry.auto.instrumentation.playws.PlayWSClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.playws.PlayWSClientDecorator.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;
import java.util.List;
import javax.net.ssl.SSLSession;
import play.shaded.ahc.io.netty.channel.Channel;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.Response;
import play.shaded.ahc.org.asynchttpclient.netty.request.NettyRequest;

public class AsyncHandlerWrapper implements AsyncHandler {
  private final AsyncHandler delegate;
  private final Span span;
  private final Span parentSpan;

  private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

  public AsyncHandlerWrapper(final AsyncHandler delegate, final Span span) {
    this.delegate = delegate;
    this.span = span;
    parentSpan = TRACER.getCurrentSpan();
  }

  @Override
  public State onBodyPartReceived(final HttpResponseBodyPart content) throws Exception {
    builder.accumulate(content);
    return delegate.onBodyPartReceived(content);
  }

  @Override
  public State onStatusReceived(final HttpResponseStatus status) throws Exception {
    builder.reset();
    builder.accumulate(status);
    return delegate.onStatusReceived(status);
  }

  @Override
  public State onHeadersReceived(final HttpHeaders httpHeaders) throws Exception {
    builder.accumulate(httpHeaders);
    return delegate.onHeadersReceived(httpHeaders);
  }

  @Override
  public Object onCompleted() throws Exception {
    final Response response = builder.build();
    if (response != null) {
      DECORATE.onResponse(span, response);
    }
    DECORATE.beforeFinish(span);
    span.end();

    if (parentSpan.getContext().isValid()) {
      try (final Scope scope = currentContextWith(parentSpan)) {
        return delegate.onCompleted();
      }
    } else {
      return delegate.onCompleted();
    }
  }

  @Override
  public void onThrowable(final Throwable throwable) {
    DECORATE.onError(span, throwable);
    DECORATE.beforeFinish(span);
    span.end();

    if (parentSpan.getContext().isValid()) {
      try (final Scope scope = currentContextWith(parentSpan)) {
        delegate.onThrowable(throwable);
      }
    } else {
      delegate.onThrowable(throwable);
    }
  }

  @Override
  public State onTrailingHeadersReceived(final HttpHeaders headers) throws Exception {
    return delegate.onTrailingHeadersReceived(headers);
  }

  @Override
  public void onHostnameResolutionAttempt(final String name) {
    delegate.onHostnameResolutionAttempt(name);
  }

  @Override
  public void onHostnameResolutionSuccess(final String name, final List list) {
    delegate.onHostnameResolutionSuccess(name, list);
  }

  @Override
  public void onHostnameResolutionFailure(final String name, final Throwable cause) {
    delegate.onHostnameResolutionFailure(name, cause);
  }

  @Override
  public void onTcpConnectAttempt(final InetSocketAddress remoteAddress) {
    delegate.onTcpConnectAttempt(remoteAddress);
  }

  @Override
  public void onTcpConnectSuccess(final InetSocketAddress remoteAddress, final Channel connection) {
    delegate.onTcpConnectSuccess(remoteAddress, connection);
  }

  @Override
  public void onTcpConnectFailure(final InetSocketAddress remoteAddress, final Throwable cause) {
    delegate.onTcpConnectFailure(remoteAddress, cause);
  }

  @Override
  public void onTlsHandshakeAttempt() {
    delegate.onTlsHandshakeAttempt();
  }

  @Override
  public void onTlsHandshakeSuccess(final SSLSession sslSession) {
    delegate.onTlsHandshakeSuccess(sslSession);
  }

  @Override
  public void onTlsHandshakeFailure(final Throwable cause) {
    delegate.onTlsHandshakeFailure(cause);
  }

  @Override
  public void onConnectionPoolAttempt() {
    delegate.onConnectionPoolAttempt();
  }

  @Override
  public void onConnectionPooled(final Channel connection) {
    delegate.onConnectionPooled(connection);
  }

  @Override
  public void onConnectionOffer(final Channel connection) {
    delegate.onConnectionOffer(connection);
  }

  @Override
  public void onRequestSend(final NettyRequest request) {
    delegate.onRequestSend(request);
  }

  @Override
  public void onRetry() {
    delegate.onRetry();
  }
}
