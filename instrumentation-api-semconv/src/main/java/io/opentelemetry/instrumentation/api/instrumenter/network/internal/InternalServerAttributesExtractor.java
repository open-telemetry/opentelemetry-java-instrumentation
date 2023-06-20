/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.FallbackNamePortGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalServerAttributesExtractor<REQUEST, RESPONSE> {

  private final ServerAttributesGetter<REQUEST, RESPONSE> getter;
  private final BiPredicate<Integer, REQUEST> captureServerPortCondition;
  private final FallbackNamePortGetter<REQUEST> fallbackNamePortGetter;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;
  private final Mode oldSemconvMode;

  public InternalServerAttributesExtractor(
      ServerAttributesGetter<REQUEST, RESPONSE> getter,
      BiPredicate<Integer, REQUEST> captureServerPortCondition,
      FallbackNamePortGetter<REQUEST> fallbackNamePortGetter,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes,
      Mode oldSemconvMode) {
    this.getter = getter;
    this.captureServerPortCondition = captureServerPortCondition;
    this.fallbackNamePortGetter = fallbackNamePortGetter;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
    this.oldSemconvMode = oldSemconvMode;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    String serverAddress = extractServerAddress(request);
    if (emitStableUrlAttributes) {
      internalSet(attributes, NetworkAttributes.SERVER_ADDRESS, serverAddress);
    }
    if (emitOldHttpAttributes) {
      internalSet(attributes, oldSemconvMode.address, serverAddress);
    }

    Integer serverPort = extractServerPort(request);
    if (serverPort != null
        && serverPort > 0
        && captureServerPortCondition.test(serverPort, request)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, NetworkAttributes.SERVER_PORT, (long) serverPort);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, oldSemconvMode.port, (long) serverPort);
      }
    }
  }

  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {
    String serverAddress = extractServerAddress(request);

    String serverSocketAddress = getter.getServerSocketAddress(request, response);
    if (serverSocketAddress != null && !serverSocketAddress.equals(serverAddress)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, NetworkAttributes.SERVER_SOCKET_ADDRESS, serverSocketAddress);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, oldSemconvMode.socketAddress, serverSocketAddress);
      }
    }

    Integer serverPort = extractServerPort(request);
    Integer serverSocketPort = getter.getServerSocketPort(request, response);
    if (serverSocketPort != null && serverSocketPort > 0 && !serverSocketPort.equals(serverPort)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, NetworkAttributes.SERVER_SOCKET_PORT, (long) serverSocketPort);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, oldSemconvMode.socketPort, (long) serverSocketPort);
      }
    }

    String serverSocketDomain = getter.getServerSocketDomain(request, response);
    if (serverSocketDomain != null && !serverSocketDomain.equals(serverAddress)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, NetworkAttributes.SERVER_SOCKET_DOMAIN, serverSocketDomain);
      }
      if (emitOldHttpAttributes && oldSemconvMode.socketDomain != null) {
        internalSet(attributes, oldSemconvMode.socketDomain, serverSocketDomain);
      }
    }
  }

  private String extractServerAddress(REQUEST request) {
    String serverAddress = getter.getServerAddress(request);
    if (serverAddress == null) {
      serverAddress = fallbackNamePortGetter.name(request);
    }
    return serverAddress;
  }

  private Integer extractServerPort(REQUEST request) {
    Integer serverPort = getter.getServerPort(request);
    if (serverPort == null) {
      serverPort = fallbackNamePortGetter.port(request);
    }
    return serverPort;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  @SuppressWarnings("ImmutableEnumChecker")
  public enum Mode {
    PEER(
        SemanticAttributes.NET_PEER_NAME,
        SemanticAttributes.NET_PEER_PORT,
        SemanticAttributes.NET_SOCK_PEER_NAME,
        SemanticAttributes.NET_SOCK_PEER_ADDR,
        SemanticAttributes.NET_SOCK_PEER_PORT),
    HOST(
        SemanticAttributes.NET_HOST_NAME,
        SemanticAttributes.NET_HOST_PORT,
        // the old semconv does not have an attribute for this
        null,
        SemanticAttributes.NET_SOCK_HOST_ADDR,
        SemanticAttributes.NET_SOCK_HOST_PORT);

    final AttributeKey<String> address;
    final AttributeKey<Long> port;
    @Nullable final AttributeKey<String> socketDomain;
    final AttributeKey<String> socketAddress;
    final AttributeKey<Long> socketPort;

    Mode(
        AttributeKey<String> address,
        AttributeKey<Long> port,
        AttributeKey<String> socketDomain,
        AttributeKey<String> socketAddress,
        AttributeKey<Long> socketPort) {
      this.address = address;
      this.port = port;
      this.socketDomain = socketDomain;
      this.socketAddress = socketAddress;
      this.socketPort = socketPort;
    }
  }
}
