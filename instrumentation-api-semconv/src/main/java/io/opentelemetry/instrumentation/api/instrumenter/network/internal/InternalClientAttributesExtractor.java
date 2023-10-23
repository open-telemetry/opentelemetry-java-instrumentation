/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.SemanticAttributes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalClientAttributesExtractor<REQUEST> {

  private final AddressAndPortExtractor<REQUEST> addressAndPortExtractor;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;

  public InternalClientAttributesExtractor(
      AddressAndPortExtractor<REQUEST> addressAndPortExtractor,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes) {
    this.addressAndPortExtractor = addressAndPortExtractor;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void onStart(AttributesBuilder attributes, REQUEST request) {
    AddressAndPort clientAddressAndPort = addressAndPortExtractor.extract(request);

    if (clientAddressAndPort.address != null) {
      if (emitStableUrlAttributes) {
        internalSet(attributes, SemanticAttributes.CLIENT_ADDRESS, clientAddressAndPort.address);
        if (clientAddressAndPort.port != null && clientAddressAndPort.port > 0) {
          internalSet(attributes, SemanticAttributes.CLIENT_PORT, (long) clientAddressAndPort.port);
        }
      }
      if (emitOldHttpAttributes) {
        internalSet(attributes, SemanticAttributes.HTTP_CLIENT_IP, clientAddressAndPort.address);
      }
    }
  }
}
