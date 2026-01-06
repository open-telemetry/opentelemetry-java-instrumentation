/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
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
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NetworkTimingEventListener extends EventListener {

  // Raw timestamp attribute keys
  private static final AttributeKey<Long> CALL_START = AttributeKey.longKey("http.call.start_time");
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
  private static final AttributeKey<Long> CALL_END = AttributeKey.longKey("http.call.end_time");

  // Singleton instance of stateless NetworkTimingEventListener
  private static final NetworkTimingEventListener INSTANCE = new NetworkTimingEventListener();

  private NetworkTimingEventListener() {}

  @Override
  public void callStart(Call call) {
    Span.current().setAttribute(CALL_START, System.currentTimeMillis());
  }

  @Override
  public void dnsStart(Call call, String domainName) {
    Span.current().setAttribute(DNS_START, System.currentTimeMillis());
  }

  @Override
  public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
    Span.current().setAttribute(DNS_END, System.currentTimeMillis());
  }

  @Override
  public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
    Span.current().setAttribute(CONNECT_START, System.currentTimeMillis());
  }

  @Override
  public void secureConnectStart(Call call) {
    Span.current().setAttribute(SECURE_CONNECT_START, System.currentTimeMillis());
  }

  @Override
  public void secureConnectEnd(Call call, @Nullable Handshake handshake) {
    Span.current().setAttribute(SECURE_CONNECT_END, System.currentTimeMillis());
  }

  @Override
  public void connectEnd(
      Call call, InetSocketAddress inetSocketAddress, Proxy proxy, @Nullable Protocol protocol) {
    Span.current().setAttribute(CONNECT_END, System.currentTimeMillis());
  }

  @Override
  public void connectFailed(
      Call call,
      InetSocketAddress inetSocketAddress,
      Proxy proxy,
      @Nullable Protocol protocol,
      IOException ioe) {}

  @Override
  public void connectionAcquired(Call call, Connection connection) {}

  @Override
  public void connectionReleased(Call call, Connection connection) {}

  @Override
  public void requestHeadersStart(Call call) {
    Span.current().setAttribute(REQUEST_HEADERS_START, System.currentTimeMillis());
  }

  @Override
  public void requestHeadersEnd(Call call, Request request) {
    Span.current().setAttribute(REQUEST_HEADERS_END, System.currentTimeMillis());
  }

  @Override
  public void requestBodyStart(Call call) {
    Span.current().setAttribute(REQUEST_BODY_START, System.currentTimeMillis());
  }

  @Override
  public void requestBodyEnd(Call call, long byteCount) {
    Span.current().setAttribute(REQUEST_BODY_END, System.currentTimeMillis());
  }

  @Override
  public void responseHeadersStart(Call call) {
    Span.current().setAttribute(RESPONSE_HEADERS_START, System.currentTimeMillis());
  }

  @Override
  public void responseHeadersEnd(Call call, Response response) {
    Span.current().setAttribute(RESPONSE_HEADERS_END, System.currentTimeMillis());
  }

  @Override
  public void responseBodyStart(Call call) {
    Span.current().setAttribute(RESPONSE_BODY_START, System.currentTimeMillis());
  }

  @Override
  public void responseBodyEnd(Call call, long byteCount) {
    Span.current().setAttribute(RESPONSE_BODY_END, System.currentTimeMillis());
  }

  @Override
  public void callEnd(Call call) {
    Span.current().setAttribute(CALL_END, System.currentTimeMillis());
  }

  /**
   * Factory for creating NetworkTimingEventListener instances. A singleton instance is returned as
   * the listener is stateless and thread-safe.
   *
   * <p>NetworkTimingEventListener captures raw network timing timestamps and adds them as
   * attributes to the current OpenTelemetry span.
   *
   * <p>Works with both synchronous and asynchronous OkHttp calls when used with proper context
   * propagation.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class Factory implements EventListener.Factory {
    @Override
    public EventListener create(Call call) {
      return INSTANCE;
    }
  }
}
