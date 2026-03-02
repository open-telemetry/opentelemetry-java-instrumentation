/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  // Timing attribute keys
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

  private final Logger eventLogger;
  private final Map<Call, LogRecordBuilder> callToLogBuilder = new ConcurrentHashMap<>();
  private static final Map<Call, Context> callToContext = new ConcurrentHashMap<>();
  // Temporary storage for timing attributes before context is available
  private final Map<Call, Map<AttributeKey<?>, Object>> deferredLogAttributes =
      new ConcurrentHashMap<>();

  private NetworkTimingEventListener(Logger eventLogger) {
    this.eventLogger = eventLogger;
  }

  /**
   * Saves the context for a call. Should be called by {@link TracingInterceptor} when context
   * storage is enabled.
   *
   * @param call the HTTP call
   * @param context the OpenTelemetry context to associate with the call
   */
  public static void saveContext(Call call, Context context) {
    callToContext.put(call, context);
  }

  /**
   * Removes the stored context for a call to prevent memory leaks.
   *
   * @param call the HTTP call
   */
  private static void removeContext(Call call) {
    callToContext.remove(call);
  }

  /**
   * Adds an attribute to either the LogRecordBuilder (if context available) or
   * deferredLogAttributes.
   *
   * @param call the HTTP call
   * @param key the attribute key
   * @param value the attribute value
   */
  private <T> void addAttribute(Call call, AttributeKey<T> key, T value) {
    // Check if context is available
    Context storedContext = callToContext.get(call);

    if (storedContext != null && callToLogBuilder.get(call) == null) {
      // Context is now available, create the builder and dump deferred attributes
      createBuilderWithDeferredAttributes(call, storedContext);
    }

    LogRecordBuilder builder = callToLogBuilder.get(call);
    if (builder != null) {
      // Builder exists, add directly
      builder.setAttribute(key, value);
    } else {
      // No builder yet, store in deferred attributes
      deferredLogAttributes.computeIfAbsent(call, k -> new ConcurrentHashMap<>()).put(key, value);
    }
  }

  /**
   * Creates a LogRecordBuilder with context and dumps all deferred attributes into it.
   *
   * @param call the HTTP call
   * @param context the OpenTelemetry context
   */
  private void createBuilderWithDeferredAttributes(Call call, Context context) {
    LogRecordBuilder builder =
        eventLogger
            .logRecordBuilder()
            .setEventName("http.client.network_timing")
            .setSeverity(Severity.INFO)
            .setContext(context);

    // Dump all deferred attributes into the builder
    Map<AttributeKey<?>, Object> deferred = deferredLogAttributes.remove(call);
    if (deferred != null) {
      for (Map.Entry<AttributeKey<?>, Object> entry : deferred.entrySet()) {
        // Safe cast: setAttribute accepts Object type and type safety was enforced at attribute
        // creation
        @SuppressWarnings("unchecked")
        AttributeKey<Object> key = (AttributeKey<Object>) entry.getKey();
        builder.setAttribute(key, entry.getValue());
      }
    }

    callToLogBuilder.put(call, builder);
  }

  /** Returns the current timestamp in nanoseconds. */
  private static long currentTimestampNanos() {
    return System.nanoTime();
  }

  @Override
  public void callStart(Call call) {
    addAttribute(call, CALL_START, currentTimestampNanos());
  }

  @Override
  public void dnsStart(Call call, String domainName) {
    addAttribute(call, DNS_START, currentTimestampNanos());
  }

  @Override
  public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList) {
    addAttribute(call, DNS_END, currentTimestampNanos());
  }

  @Override
  public void connectStart(Call call, InetSocketAddress inetSocketAddress, Proxy proxy) {
    addAttribute(call, CONNECT_START, currentTimestampNanos());
  }

  @Override
  public void secureConnectStart(Call call) {
    addAttribute(call, SECURE_CONNECT_START, currentTimestampNanos());
  }

  @Override
  public void secureConnectEnd(Call call, @Nullable Handshake handshake) {
    addAttribute(call, SECURE_CONNECT_END, currentTimestampNanos());
  }

  @Override
  public void connectEnd(
      Call call, InetSocketAddress inetSocketAddress, Proxy proxy, @Nullable Protocol protocol) {
    addAttribute(call, CONNECT_END, currentTimestampNanos());
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
    addAttribute(call, REQUEST_HEADERS_START, currentTimestampNanos());
  }

  @Override
  public void requestHeadersEnd(Call call, Request request) {
    addAttribute(call, REQUEST_HEADERS_END, currentTimestampNanos());
  }

  @Override
  public void requestBodyStart(Call call) {
    addAttribute(call, REQUEST_BODY_START, currentTimestampNanos());
  }

  @Override
  public void requestBodyEnd(Call call, long byteCount) {
    addAttribute(call, REQUEST_BODY_END, currentTimestampNanos());
  }

  @Override
  public void responseHeadersStart(Call call) {
    addAttribute(call, RESPONSE_HEADERS_START, currentTimestampNanos());
  }

  @Override
  public void responseHeadersEnd(Call call, Response response) {
    addAttribute(call, RESPONSE_HEADERS_END, currentTimestampNanos());
  }

  @Override
  public void responseBodyStart(Call call) {
    addAttribute(call, RESPONSE_BODY_START, currentTimestampNanos());
  }

  @Override
  public void responseBodyEnd(Call call, long byteCount) {
    addAttribute(call, RESPONSE_BODY_END, currentTimestampNanos());
  }

  @Override
  public void callEnd(Call call) {
    addAttribute(call, CALL_END, currentTimestampNanos());
    emitLogAndCleanup(call);
  }

  @Override
  public void callFailed(Call call, IOException ioe) {
    addAttribute(call, CALL_END, currentTimestampNanos());
    emitLogAndCleanup(call);
  }

  private void emitLogAndCleanup(Call call) {
    LogRecordBuilder builder = callToLogBuilder.remove(call);
    if (builder != null) {
      builder.setTimestamp(Instant.now());
      builder.emit();
    }

    // Clean up deferred attributes and context to prevent memory leaks
    deferredLogAttributes.remove(call);
    removeContext(call);
  }

  /**
   * Factory for creating NetworkTimingEventListener instance.
   *
   * <p>NetworkTimingEventListener captures network timing events and emits them as a single log
   * record with all timing attributes when the call completes.
   *
   * <p>Works with both synchronous and asynchronous OkHttp calls when used with proper context
   * propagation.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class Factory implements EventListener.Factory {
    private final NetworkTimingEventListener sharedListener;

    public Factory(OpenTelemetry openTelemetry) {
      Logger eventLogger = openTelemetry.getLogsBridge().get("io.opentelemetry.okhttp-3.0");
      this.sharedListener = new NetworkTimingEventListener(eventLogger);
    }

    @Override
    public EventListener create(Call call) {
      return sharedListener;
    }
  }
}
