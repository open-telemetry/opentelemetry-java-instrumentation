/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An okhttp {@link EventListener} that drives the lifecycle of the client span. The span is started
 * on {@link #callStart(Call)}. When network timing capture is enabled, each network phase callback
 * records the elapsed time since the call started as a relative-offset (nanoseconds) attribute on
 * the span, and the span is ended on {@link #callEnd(Call)} / {@link #callFailed(Call,
 * IOException)} so that all phases — including the response body read, which completes after the
 * response headers are received — are captured. When timing capture is disabled, the span is ended
 * as soon as the application interceptor chain completes (see {@link OkHttpClientCallState}).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class TracingEventListener extends EventListener {

  // network phase timing attribute keys; values are nanoseconds elapsed since callStart
  private static final AttributeKey<Long> CALL_START = AttributeKey.longKey("http.call.start_time");
  private static final AttributeKey<Long> CALL_END = AttributeKey.longKey("http.call.end_time");
  private static final AttributeKey<Long> DNS_START = AttributeKey.longKey("http.dns.start_time");
  private static final AttributeKey<Long> DNS_END = AttributeKey.longKey("http.dns.end_time");
  private static final AttributeKey<Long> CONNECT_START =
      AttributeKey.longKey("http.connect.start_time");
  private static final AttributeKey<Long> CONNECT_END =
      AttributeKey.longKey("http.connect.end_time");
  private static final AttributeKey<Long> SECURE_CONNECT_START =
      AttributeKey.longKey("http.secure_connect.start_time");
  private static final AttributeKey<Long> SECURE_CONNECT_END =
      AttributeKey.longKey("http.secure_connect.end_time");
  private static final AttributeKey<Long> REQUEST_HEADERS_START =
      AttributeKey.longKey("http.request.headers.start_time");
  private static final AttributeKey<Long> REQUEST_HEADERS_END =
      AttributeKey.longKey("http.request.headers.end_time");
  private static final AttributeKey<Long> REQUEST_BODY_START =
      AttributeKey.longKey("http.request.body.start_time");
  private static final AttributeKey<Long> REQUEST_BODY_END =
      AttributeKey.longKey("http.request.body.end_time");
  private static final AttributeKey<Long> RESPONSE_HEADERS_START =
      AttributeKey.longKey("http.response.headers.start_time");
  private static final AttributeKey<Long> RESPONSE_HEADERS_END =
      AttributeKey.longKey("http.response.headers.end_time");
  private static final AttributeKey<Long> RESPONSE_BODY_START =
      AttributeKey.longKey("http.response.body.start_time");
  private static final AttributeKey<Long> RESPONSE_BODY_END =
      AttributeKey.longKey("http.response.body.end_time");

  private final Instrumenter<Call, Response> instrumenter;
  @Nullable private final EventListener delegate;

  // per-call state lives on the request tag (see OkHttpClientCallState); cached here on callStart
  @Nullable private OkHttpClientCallState state;

  private TracingEventListener(
      Instrumenter<Call, Response> instrumenter, @Nullable EventListener delegate) {
    this.instrumenter = instrumenter;
    this.delegate = delegate;
  }

  private void record(AttributeKey<Long> key) {
    OkHttpClientCallState state = this.state;
    if (state == null || !state.captureTimings()) {
      return;
    }
    Context context = state.context();
    if (context == null) {
      return;
    }
    Span.fromContext(context).setAttribute(key, System.nanoTime() - state.startNanos());
  }

  @Override
  public void callStart(Call call) {
    if (delegate != null) {
      delegate.callStart(call);
    }
    state = OkHttpClientCallState.get(call);
    if (state == null) {
      return;
    }
    state.startSpan(instrumenter, call);
    Context context = state.context();
    if (context != null && state.captureTimings()) {
      Span.fromContext(context).setAttribute(CALL_START, 0L);
    }
  }

  @Override
  public void dnsStart(Call call, String domainName) {
    if (delegate != null) {
      delegate.dnsStart(call, domainName);
    }
    record(DNS_START);
  }

  @Override
  public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
    if (delegate != null) {
      delegate.dnsEnd(call, domainName, inetAddressList);
    }
    record(DNS_END);
  }

  @Override
  public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
    if (delegate != null) {
      delegate.connectStart(call, inetSocketAddress, proxy);
    }
    record(CONNECT_START);
  }

  @Override
  public void secureConnectStart(Call call) {
    if (delegate != null) {
      delegate.secureConnectStart(call);
    }
    record(SECURE_CONNECT_START);
  }

  @Override
  public void secureConnectEnd(Call call, @Nullable Handshake handshake) {
    if (delegate != null) {
      delegate.secureConnectEnd(call, handshake);
    }
    record(SECURE_CONNECT_END);
  }

  @Override
  public void connectEnd(
      Call call, InetSocketAddress inetSocketAddress, Proxy proxy, @Nullable Protocol protocol) {
    if (delegate != null) {
      delegate.connectEnd(call, inetSocketAddress, proxy, protocol);
    }
    record(CONNECT_END);
  }

  @Override
  public void connectFailed(
      Call call,
      InetSocketAddress inetSocketAddress,
      Proxy proxy,
      @Nullable Protocol protocol,
      IOException ioe) {
    if (delegate != null) {
      delegate.connectFailed(call, inetSocketAddress, proxy, protocol, ioe);
    }
  }

  @Override
  public void connectionAcquired(Call call, Connection connection) {
    if (delegate != null) {
      delegate.connectionAcquired(call, connection);
    }
    if (state != null) {
      state.setConnection(connection);
    }
  }

  @Override
  public void connectionReleased(Call call, Connection connection) {
    if (delegate != null) {
      delegate.connectionReleased(call, connection);
    }
  }

  @Override
  public void requestHeadersStart(Call call) {
    if (delegate != null) {
      delegate.requestHeadersStart(call);
    }
    record(REQUEST_HEADERS_START);
  }

  @Override
  public void requestHeadersEnd(Call call, Request request) {
    if (delegate != null) {
      delegate.requestHeadersEnd(call, request);
    }
    record(REQUEST_HEADERS_END);
  }

  @Override
  public void requestBodyStart(Call call) {
    if (delegate != null) {
      delegate.requestBodyStart(call);
    }
    record(REQUEST_BODY_START);
  }

  @Override
  public void requestBodyEnd(Call call, long byteCount) {
    if (delegate != null) {
      delegate.requestBodyEnd(call, byteCount);
    }
    record(REQUEST_BODY_END);
  }

  @Override
  public void responseHeadersStart(Call call) {
    if (delegate != null) {
      delegate.responseHeadersStart(call);
    }
    record(RESPONSE_HEADERS_START);
  }

  @Override
  public void responseHeadersEnd(Call call, Response response) {
    if (delegate != null) {
      delegate.responseHeadersEnd(call, response);
    }
    if (state != null) {
      state.setResponse(response);
    }
    record(RESPONSE_HEADERS_END);
  }

  @Override
  public void responseBodyStart(Call call) {
    if (delegate != null) {
      delegate.responseBodyStart(call);
    }
    record(RESPONSE_BODY_START);
  }

  @Override
  public void responseBodyEnd(Call call, long byteCount) {
    if (delegate != null) {
      delegate.responseBodyEnd(call, byteCount);
    }
    record(RESPONSE_BODY_END);
  }

  @Override
  public void callEnd(Call call) {
    if (delegate != null) {
      delegate.callEnd(call);
    }
    record(CALL_END);
    if (state != null) {
      state.networkComplete(call, null);
    }
  }

  @Override
  public void callFailed(Call call, IOException ioe) {
    if (delegate != null) {
      delegate.callFailed(call, ioe);
    }
    record(CALL_END);
    if (state != null) {
      state.networkComplete(call, ioe);
    }
  }

  /**
   * Factory for {@link TracingEventListener}. A new listener instance is created per call, so that
   * per-call timing state does not need to be shared. Any {@link EventListener.Factory} already
   * configured on the client is preserved and delegated to.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class Factory implements EventListener.Factory {
    private final Instrumenter<Call, Response> instrumenter;
    @Nullable private final EventListener.Factory delegateFactory;

    public Factory(
        Instrumenter<Call, Response> instrumenter,
        @Nullable EventListener.Factory delegateFactory) {
      this.instrumenter = instrumenter;
      this.delegateFactory = delegateFactory;
    }

    @Override
    public EventListener create(Call call) {
      EventListener delegate = delegateFactory != null ? delegateFactory.create(call) : null;
      return new TracingEventListener(instrumenter, delegate);
    }
  }
}
