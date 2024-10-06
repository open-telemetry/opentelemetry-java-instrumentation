/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.NetworkAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/general/attributes.md#other-network-attributes">network
 * attributes</a>.
 */
public final class DbNetworkAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the network attributes extractor.
   *
   * @see InstrumenterBuilder#addAttributesExtractor(AttributesExtractor)
   */
  public static <REQUEST, RESPONSE> DbNetworkAttributesExtractor<REQUEST, RESPONSE> create(
      DbNetworkAttributesGetter<REQUEST, RESPONSE> getter) {
    return new DbNetworkAttributesExtractor<>(getter);
  }

  private final DbNetworkAttributesGetter<REQUEST, RESPONSE> getter;

  DbNetworkAttributesExtractor(DbNetworkAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @Override
  @SuppressWarnings("deprecation") // to support old database semantic conventions
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    if (SemconvStability.emitOldDatabaseSemconv()) {
      String networkType = getter.getNetworkType(request, response);
      if (networkType != null) {
        internalSet(attributes, NetworkAttributes.NETWORK_TYPE, networkType);
      }
    }

    String peerAddress = getter.getNetworkPeerAddress(request, response);
    if (peerAddress != null) {
      internalSet(attributes, NetworkAttributes.NETWORK_PEER_ADDRESS, peerAddress);

      Integer peerPort = getter.getNetworkPeerPort(request, response);
      if (peerPort != null && peerPort > 0) {
        internalSet(attributes, NetworkAttributes.NETWORK_PEER_PORT, (long) peerPort);
      }
    }
  }
}
