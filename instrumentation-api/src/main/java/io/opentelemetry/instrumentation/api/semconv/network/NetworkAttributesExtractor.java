/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalNetworkAttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/general/attributes.md#other-network-attributes">network
 * attributes</a>.
 *
 * @since 2.0.0
 */
public final class NetworkAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the network attributes extractor.
   *
   * @see InstrumenterBuilder#addAttributesExtractor(AttributesExtractor)
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
            /* captureProtocolAttributes= */ true,
            /* captureLocalSocketAttributes= */ true);
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
