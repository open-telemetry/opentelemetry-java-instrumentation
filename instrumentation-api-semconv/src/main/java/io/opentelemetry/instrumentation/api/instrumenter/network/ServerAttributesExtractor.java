/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.ServerAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/general/attributes.md#server-attributes">server
 * attributes</a>.
 */
public final class ServerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the server attributes extractor.
   *
   * @see InstrumenterBuilder#addAttributesExtractor(AttributesExtractor)
   */
  public static <REQUEST, RESPONSE> ServerAttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return new ServerAttributesExtractor<>(getter, InternalServerAttributesExtractor.Mode.PEER);
  }

  /**
   * Returns a new {@link ServerAttributesExtractor} that will use the passed {@link
   * ServerAttributesGetter}.
   *
   * @deprecated This method will be removed in the 2.0 release. It was only introduced to ease the
   *     transition from using the old {@code NetServerAttributesGetter} to the new {@code
   *     ...network} attribute getter classes.
   */
  @Deprecated
  public static <REQUEST, RESPONSE>
      ServerAttributesExtractor<REQUEST, RESPONSE> createForServerSide(
          ServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return new ServerAttributesExtractor<>(getter, InternalServerAttributesExtractor.Mode.HOST);
  }

  private final InternalServerAttributesExtractor<REQUEST> internalExtractor;

  ServerAttributesExtractor(
      ServerAttributesGetter<REQUEST, RESPONSE> getter,
      InternalServerAttributesExtractor.Mode mode) {
    internalExtractor =
        new InternalServerAttributesExtractor<>(
            new ServerAddressAndPortExtractor<>(getter, AddressAndPortExtractor.noop()),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv(),
            mode);
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
      @Nullable Throwable error) {}
}
