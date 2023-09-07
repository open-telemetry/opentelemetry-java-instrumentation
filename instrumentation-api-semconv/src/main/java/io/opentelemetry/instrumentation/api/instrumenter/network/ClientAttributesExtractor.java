/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.FallbackAddressPortExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/trace/semantic_conventions/span-general.md#client-attributes">client
 * attributes</a>.
 */
public final class ClientAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Returns a new {@link ClientAttributesExtractor} that will use the passed {@link
   * ClientAttributesGetter}.
   */
  public static <REQUEST, RESPONSE> ClientAttributesExtractor<REQUEST, RESPONSE> create(
      ClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new ClientAttributesExtractor<>(getter);
  }

  private final InternalClientAttributesExtractor<REQUEST, RESPONSE> internalExtractor;

  ClientAttributesExtractor(ClientAttributesGetter<REQUEST, RESPONSE> getter) {
    internalExtractor =
        new InternalClientAttributesExtractor<>(
            getter,
            FallbackAddressPortExtractor.noop(),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv());
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalExtractor.onStart(attributes, request);
  }

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
