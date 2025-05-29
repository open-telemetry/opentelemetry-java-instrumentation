/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Adds events to {@link Span}s for the enumerated protocols and situations
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public enum ProtocolSpecificEvent {
  /**
   * The event after which point the server or client transmits or receives, respectively, in one of
   * the signified upgraded protocols, per <a
   * href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Protocol_upgrade_mechanism">protocol
   * upgrade mechanism</a>.
   */
  SWITCHING_PROTOCOLS("http.response.status_code.101.upgrade") {

    @Override
    void addEvent(Context context, HttpRequest request, HttpResponse response) {
      Span.fromContext(context)
          .addEvent(
              eventName(),
              Attributes.of(
                  SWITCHING_PROTOCOLS_FROM_KEY,
                  request != null ? request.protocolVersion().text() : "unknown",
                  // pulls out all possible values emitted by upgrade header, per:
                  // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Upgrade
                  SWITCHING_PROTOCOLS_TO_KEY,
                  response.headers().getAll("upgrade")));
    }
  };

  public static final AttributeKey<String> SWITCHING_PROTOCOLS_FROM_KEY =
      stringKey("network.protocol.from");
  public static final AttributeKey<List<String>> SWITCHING_PROTOCOLS_TO_KEY =
      stringArrayKey("network.protocol.to");

  private final String eventName;

  ProtocolSpecificEvent(String eventName) {
    this.eventName = eventName;
  }

  public String eventName() {
    return eventName;
  }

  abstract void addEvent(
      Context context, @Nullable HttpRequest request, @Nullable HttpResponse response);
}
