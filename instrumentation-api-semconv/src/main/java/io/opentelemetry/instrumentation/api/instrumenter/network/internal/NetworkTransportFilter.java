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
@FunctionalInterface
public interface NetworkTransportFilter {

  boolean shouldAddNetworkTransport(
      @Nullable String protocolName,
      @Nullable String protocolVersion,
      @Nullable String proposedTransport);

  static NetworkTransportFilter alwaysTrue() {
    return (protocolName, protocolVersion, proposedTransport) -> true;
  }
}
