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
 * @deprecated Use {@link NetServerAttributesGetter} and its {@link
 *     NetServerAttributesGetter#getPeerSocketAddress(Object)} {@link
 *     NetServerAttributesGetter#getHostSocketAddress(Object)} methods instead.
 */
@Deprecated
public abstract class InetSocketAddressNetServerAttributesGetter<REQUEST, RESPONSE>
    implements NetServerAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  @Override
  public abstract InetSocketAddress getPeerSocketAddress(REQUEST request);

  // optional
  @Nullable
  @Override
  public abstract InetSocketAddress getHostSocketAddress(REQUEST request);
}
