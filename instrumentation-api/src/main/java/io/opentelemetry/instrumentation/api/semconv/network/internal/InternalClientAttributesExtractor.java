/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_PORT;

import io.opentelemetry.api.common.AttributesBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalClientAttributesExtractor<REQUEST> {

  private final AddressAndPortExtractor<REQUEST> addressAndPortExtractor;
  private final boolean capturePort;

  public InternalClientAttributesExtractor(
      AddressAndPortExtractor<REQUEST> addressAndPortExtractor, boolean capturePort) {
    this.addressAndPortExtractor = addressAndPortExtractor;
    this.capturePort = capturePort;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    AddressAndPort clientAddressAndPort = addressAndPortExtractor.extract(request);

    if (clientAddressAndPort.address != null) {
      attributes.put(CLIENT_ADDRESS, clientAddressAndPort.address);
      if (capturePort && clientAddressAndPort.port != null && clientAddressAndPort.port > 0) {
        attributes.put(CLIENT_PORT, (long) clientAddressAndPort.port);
      }
    }
  }
}
