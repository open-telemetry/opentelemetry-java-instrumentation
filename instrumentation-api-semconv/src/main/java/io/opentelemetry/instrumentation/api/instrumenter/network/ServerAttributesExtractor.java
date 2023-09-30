/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.FallbackAddressPortExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/trace/semantic_conventions/span-general.md#server-attributes">server
 * attributes</a>.
 */
public final class ServerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Returns a new {@link ServerAttributesExtractor} that will use the passed {@link
   * ServerAttributesGetter}.
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

  private final InternalServerAttributesExtractor<REQUEST, RESPONSE> internalExtractor;

  ServerAttributesExtractor(
      ServerAttributesGetter<REQUEST, RESPONSE> getter,
      InternalServerAttributesExtractor.Mode mode) {
    // the ServerAttributesExtractor will always emit new semconv
    internalExtractor =
        new InternalServerAttributesExtractor<>(
            getter,
            (port, request) -> true,
            FallbackAddressPortExtractor.noop(),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv(),
            mode,
            /* captureServerSocketAttributes= */ true);
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
