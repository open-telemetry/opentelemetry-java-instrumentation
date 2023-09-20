/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.network.NetworkAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetworkAttributesExtractor<REQUEST, RESPONSE> {

  private final NetworkAttributesGetter<REQUEST, RESPONSE> getter;
  private final NetworkTransportFilter networkTransportFilter;
  private final boolean emitStableUrlAttributes;
  private final boolean emitOldHttpAttributes;

  public InternalNetworkAttributesExtractor(
      NetworkAttributesGetter<REQUEST, RESPONSE> getter,
      NetworkTransportFilter networkTransportFilter,
      boolean emitStableUrlAttributes,
      boolean emitOldHttpAttributes) {
    this.getter = getter;
    this.networkTransportFilter = networkTransportFilter;
    this.emitStableUrlAttributes = emitStableUrlAttributes;
    this.emitOldHttpAttributes = emitOldHttpAttributes;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void onEnd(AttributesBuilder attributes, REQUEST request, @Nullable RESPONSE response) {
    String protocolName = lowercase(getter.getNetworkProtocolName(request, response));
    String protocolVersion = lowercase(getter.getNetworkProtocolVersion(request, response));

    if (emitStableUrlAttributes) {
      String transport = lowercase(getter.getNetworkTransport(request, response));
      if (networkTransportFilter.shouldAddNetworkTransport(
          protocolName, protocolVersion, transport)) {
        internalSet(attributes, SemanticAttributes.NETWORK_TRANSPORT, transport);
      }
      internalSet(
          attributes,
          SemanticAttributes.NETWORK_TYPE,
          lowercase(getter.getNetworkType(request, response)));
      internalSet(attributes, SemanticAttributes.NETWORK_PROTOCOL_NAME, protocolName);
      internalSet(attributes, SemanticAttributes.NETWORK_PROTOCOL_VERSION, protocolVersion);
    }
    if (emitOldHttpAttributes) {
      // net.transport and net.sock.family are not 1:1 convertible with network.transport and
      // network.type; they must be handled separately in the old net.* extractors
      internalSet(attributes, SemanticAttributes.NET_PROTOCOL_NAME, protocolName);
      internalSet(attributes, SemanticAttributes.NET_PROTOCOL_VERSION, protocolVersion);
    }
  }

  @Nullable
  private static String lowercase(@Nullable String str) {
    return str == null ? null : str.toLowerCase(Locale.ROOT);
  }
}
