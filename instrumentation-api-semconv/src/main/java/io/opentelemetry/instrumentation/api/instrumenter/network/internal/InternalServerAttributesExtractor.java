/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.SemanticAttributes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalServerAttributesExtractor<REQUEST> {

  private final AddressAndPortExtractor<REQUEST> addressAndPortExtractor;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;
  private final Mode oldSemconvMode;

  public InternalServerAttributesExtractor(
      AddressAndPortExtractor<REQUEST> addressAndPortExtractor,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes,
      Mode oldSemconvMode) {
    this.addressAndPortExtractor = addressAndPortExtractor;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
    this.oldSemconvMode = oldSemconvMode;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    AddressAndPort serverAddressAndPort = addressAndPortExtractor.extract(request);

    if (serverAddressAndPort.address != null) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, SemanticAttributes.SERVER_ADDRESS, serverAddressAndPort.address);
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, oldSemconvMode.address, serverAddressAndPort.address);
      }

      if (serverAddressAndPort.port != null && serverAddressAndPort.port > 0) {
        if (emitStableUrlAttributes) {
          internalSet(attributes, SemanticAttributes.SERVER_PORT, (long) serverAddressAndPort.port);
        }
        if (emitOldHttpAttributes) {
          internalSet(attributes, oldSemconvMode.port, (long) serverAddressAndPort.port);
        }
      }
    }
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
    PEER(SemanticAttributes.NET_PEER_NAME, SemanticAttributes.NET_PEER_PORT),
    HOST(SemanticAttributes.NET_HOST_NAME, SemanticAttributes.NET_HOST_PORT);

    final AttributeKey<String> address;
    final AttributeKey<Long> port;

    Mode(AttributeKey<String> address, AttributeKey<Long> port) {
      this.address = address;
      this.port = port;
    }
  }
}
