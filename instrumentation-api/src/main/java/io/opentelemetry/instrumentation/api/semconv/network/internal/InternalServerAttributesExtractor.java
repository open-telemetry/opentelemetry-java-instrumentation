/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributesBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalServerAttributesExtractor<REQUEST> {

  private final AddressAndPortExtractor<REQUEST> addressAndPortExtractor;

  public InternalServerAttributesExtractor(
      AddressAndPortExtractor<REQUEST> addressAndPortExtractor) {
    this.addressAndPortExtractor = addressAndPortExtractor;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {
    AddressAndPort serverAddressAndPort = addressAndPortExtractor.extract(request);

    if (serverAddressAndPort.address != null) {
      attributes.put(SERVER_ADDRESS, serverAddressAndPort.address);

      if (serverAddressAndPort.port != null && serverAddressAndPort.port > 0) {
        attributes.put(SERVER_PORT, (long) serverAddressAndPort.port);
      }
    }
  }
}
