/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/trace/semantic_conventions/span-general.md#network-attributes">network
 * attributes</a>.
 */
public final class NetworkAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Returns a new {@link NetworkAttributesExtractor} that will use the passed {@link
   * NetworkAttributesGetter}.
   */
  public static <REQUEST, RESPONSE> NetworkAttributesExtractor<REQUEST, RESPONSE> create(
      NetworkAttributesGetter<REQUEST, RESPONSE> getter) {
    return new NetworkAttributesExtractor<>(getter);
  }

  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalExtractor;

  NetworkAttributesExtractor(NetworkAttributesGetter<REQUEST, RESPONSE> getter) {
    internalExtractor =
        new InternalNetworkAttributesExtractor<>(
            getter,
            AddressAndPortExtractor.noop(),
            AddressAndPortExtractor.noop(),
            /* captureNetworkTransportAndType= */ true,
            /* captureLocalSocketAttributes= */ true,
            // capture the old net.sock.peer.name attr for backwards compatibility
            /* captureOldPeerDomainAttribute= */ true,
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv());
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalExtractor.onEnd(attributes, request, response);
  }
}
