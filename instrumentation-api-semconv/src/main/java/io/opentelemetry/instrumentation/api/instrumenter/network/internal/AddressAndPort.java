/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AddressAndPort implements FallbackAddressPortExtractor.AddressPortSink {

  @Nullable String address;
  @Nullable Integer port;

  @Override
  public void setAddress(String address) {
    this.address = address;
  }

  @Override
  public void setPort(int port) {
    this.port = port;
  }

  @Nullable
  public String getAddress() {
    return address;
  }
}
