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
 * attributes</a> from a {@link InetSocketAddress}.
 *
 * @deprecated Use {@link NetClientAttributesGetter} and its {@link
 *     NetClientAttributesGetter#getPeerSocketAddress(Object, Object)} method instead.
 */
@Deprecated
public abstract class InetSocketAddressNetClientAttributesGetter<REQUEST, RESPONSE>
    implements NetClientAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  @Override
  public abstract InetSocketAddress getPeerSocketAddress(
      REQUEST request, @Nullable RESPONSE response);
}
