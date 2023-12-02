/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import io.opentelemetry.instrumentation.api.semconv.network.ClientAttributesGetter;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClientAddressAndPortExtractor<REQUEST>
    implements AddressAndPortExtractor<REQUEST> {

  private final ClientAttributesGetter<REQUEST> getter;
  private final AddressAndPortExtractor<REQUEST> fallbackAddressAndPortExtractor;

  public ClientAddressAndPortExtractor(
      ClientAttributesGetter<REQUEST> getter,
      AddressAndPortExtractor<REQUEST> fallbackAddressAndPortExtractor) {
    this.getter = getter;
    this.fallbackAddressAndPortExtractor = fallbackAddressAndPortExtractor;
  }

  @Override
  public void extract(AddressPortSink sink, REQUEST request) {
    String address = getter.getClientAddress(request);
    Integer port = getter.getClientPort(request);
    if (address == null && port == null) {
      fallbackAddressAndPortExtractor.extract(sink, request);
    } else {
      sink.setAddress(address);
      sink.setPort(port);
    }
  }
}
