/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalServerAttributesExtractor<REQUEST, RESPONSE> {

  private final ServerAttributesGetter<REQUEST, RESPONSE> getter;
  private final BiPredicate<Integer, REQUEST> captureServerPortCondition;
  private final FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;
  private final Mode oldSemconvMode;
  private final boolean captureServerSocketAttributes;

  public InternalServerAttributesExtractor(
      ServerAttributesGetter<REQUEST, RESPONSE> getter,
      BiPredicate<Integer, REQUEST> captureServerPortCondition,
      FallbackAddressPortExtractor<REQUEST> fallbackAddressPortExtractor,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes,
      Mode oldSemconvMode,
      boolean captureServerSocketAttributes) {
    this.getter = getter;
    this.captureServerPortCondition = captureServerPortCondition;
    this.fallbackAddressPortExtractor = fallbackAddressPortExtractor;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
    this.oldSemconvMode = oldSemconvMode;
    this.captureServerSocketAttributes = captureServerSocketAttributes;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    AddressAndPort serverAddressAndPort = extractServerAddressAndPort(request);

    if (emitStableUrlAttributes) {
      internalSet(attributes, SemanticAttributes.SERVER_ADDRESS, serverAddressAndPort.address);
    }
    if (emitOldHttpAttributes) {
      internalSet(attributes, oldSemconvMode.address, serverAddressAndPort.address);
    }

    if (serverAddressAndPort.port != null
        && serverAddressAndPort.port > 0
        && captureServerPortCondition.test(serverAddressAndPort.port, request)) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, SemanticAttributes.SERVER_PORT, (long) serverAddressAndPort.port);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, oldSemconvMode.port, (long) serverAddressAndPort.port);
      }
    }
  }

  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {
    AddressAndPort serverAddressAndPort = extractServerAddressAndPort(request);

    String serverSocketAddress = getter.getServerSocketAddress(request, response);
    if (serverSocketAddress != null && !serverSocketAddress.equals(serverAddressAndPort.address)) {
      if (emitStableUrlAttributes && captureServerSocketAttributes) {
        internalSet(attributes, SemanticAttributes.SERVER_SOCKET_ADDRESS, serverSocketAddress);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, oldSemconvMode.socketAddress, serverSocketAddress);
      }
    }

    Integer serverSocketPort = getter.getServerSocketPort(request, response);
    if (serverSocketPort != null
        && serverSocketPort > 0
        && !serverSocketPort.equals(serverAddressAndPort.port)) {
      if (emitStableUrlAttributes && captureServerSocketAttributes) {
        internalSet(attributes, SemanticAttributes.SERVER_SOCKET_PORT, (long) serverSocketPort);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, oldSemconvMode.socketPort, (long) serverSocketPort);
      }
    }

    String serverSocketDomain = getter.getServerSocketDomain(request, response);
    if (serverSocketDomain != null && !serverSocketDomain.equals(serverAddressAndPort.address)) {
      if (emitStableUrlAttributes && captureServerSocketAttributes) {
        internalSet(attributes, SemanticAttributes.SERVER_SOCKET_DOMAIN, serverSocketDomain);
      }
      if (emitOldHttpAttributes && oldSemconvMode.socketDomain != null) {
        internalSet(attributes, oldSemconvMode.socketDomain, serverSocketDomain);
      }
    }
  }

  private AddressAndPort extractServerAddressAndPort(REQUEST request) {
    AddressAndPort addressAndPort = new AddressAndPort();
    addressAndPort.address = getter.getServerAddress(request);
    addressAndPort.port = getter.getServerPort(request);
    if (addressAndPort.address == null && addressAndPort.port == null) {
      fallbackAddressPortExtractor.extract(addressAndPort, request);
    }
    return addressAndPort;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  @SuppressWarnings({
    "ImmutableEnumChecker",
    "deprecation"
  }) // until old http semconv are dropped in 2.0
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
