/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import java.net.URI;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a> from an {@link URI}.
 */
public abstract class UriNetAttributesExtractor<REQUEST, RESPONSE>
    extends NetAttributesExtractor<REQUEST, RESPONSE> {

  /**
   * This method will be called twice: both when the request starts ({@code response} is always null
   * then) and when the response ends. This way it is possible to capture net attributes in both
   * phases of processing.
   */
  @Nullable
  public abstract URI getUri(REQUEST request, @Nullable RESPONSE response);

  @Override
  @Nullable
  public final String peerName(REQUEST request, @Nullable RESPONSE response) {
    URI address = getUri(request, response);
    return address != null ? address.getHost() : null;
  }

  @Override
  @Nullable
  public final Integer peerPort(REQUEST request, @Nullable RESPONSE response) {
    URI address = getUri(request, response);
    if (address == null) {
      return null;
    }
    int port = address.getPort();
    if (port != -1) {
      return port;
    }
    // return De
    return address != null ? address.getPort() : null;
  }

  @Override
  @Nullable
  public final String peerIp(REQUEST request, @Nullable RESPONSE response) {
    return null;
  }
}
