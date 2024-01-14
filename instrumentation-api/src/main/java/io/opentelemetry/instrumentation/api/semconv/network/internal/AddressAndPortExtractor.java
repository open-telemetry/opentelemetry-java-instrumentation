/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface AddressAndPortExtractor<REQUEST> {

  default AddressAndPort extract(REQUEST request) {
    AddressAndPort addressAndPort = new AddressAndPort();
    extract(addressAndPort, request);
    return addressAndPort;
  }

  void extract(AddressPortSink sink, REQUEST request);

  static <REQUEST> AddressAndPortExtractor<REQUEST> noop() {
    return (sink, request) -> {};
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  interface AddressPortSink {

    void setAddress(String address);

    void setPort(Integer port);
  }
}
