/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkTransportFilter;
import javax.annotation.Nullable;

enum HttpNetworkTransportFilter implements NetworkTransportFilter {
  INSTANCE;

  @Override
  public boolean shouldAddNetworkTransport(
      @Nullable String protocolName,
      @Nullable String protocolVersion,
      @Nullable String proposedTransport) {
    // tcp is the default transport for http/1* and http/2*, we're skipping it
    if ("http".equals(protocolName)
        && protocolVersion != null
        && (protocolVersion.startsWith("1") || protocolVersion.startsWith("2"))
        && "tcp".equals(proposedTransport)) {
      return false;
    }
    // udp is the default transport for http/3*, we're skipping it
    if ("http".equals(protocolName)
        && protocolVersion != null
        && protocolVersion.startsWith("3")
        && "udp".equals(proposedTransport)) {
      return false;
    }
    return true;
  }
}
