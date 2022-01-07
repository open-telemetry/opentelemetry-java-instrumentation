/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a>. It is common to have access to {@link java.net.InetSocketAddress}, in which case
 * it is more convenient to use {@link InetSocketAddressNetClientAttributesExtractor}.
 */
public interface NetAttributesAdapter<REQUEST, RESPONSE> {

  @Nullable
  String transport(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String peerName(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  Integer peerPort(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String peerIp(REQUEST request, @Nullable RESPONSE response);
}
