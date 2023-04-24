/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a> from a {@link InetSocketAddress}. Most network libraries will provide access to a
 * {@link InetSocketAddress} so this is a convenient alternative to {@link
 * NetClientAttributesExtractor}. There is no meaning to implement both in the same instrumentation.
 */
public abstract class InetSocketAddressNetClientAttributesGetter<REQUEST, RESPONSE>
    implements NetClientAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  protected abstract InetSocketAddress getPeerSocketAddress(
      REQUEST request, @Nullable RESPONSE response);

  @Nullable
  @Override
  public String getSockFamily(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getSockFamily(getPeerSocketAddress(request, response), null);
  }

  @Override
  @Nullable
  public final String getSockPeerAddr(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getHostAddress(getPeerSocketAddress(request, response));
  }

  @Override
  @Nullable
  public String getSockPeerName(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getHostName(getPeerSocketAddress(request, response));
  }

  @Nullable
  @Override
  public Integer getSockPeerPort(REQUEST request, @Nullable RESPONSE response) {
    return InetSocketAddressUtil.getPort(getPeerSocketAddress(request, response));
  }
}
