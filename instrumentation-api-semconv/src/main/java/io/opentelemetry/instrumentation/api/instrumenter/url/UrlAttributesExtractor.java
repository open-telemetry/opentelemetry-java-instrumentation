/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.url;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.InternalUrlAttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/url.md#attributes">URL
 * attributes</a>.
 */
public final class UrlAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Returns a new {@link UrlAttributesExtractor} that will use the passed {@link
   * UrlAttributesGetter}.
   */
  public static <REQUEST, RESPONSE> UrlAttributesExtractor<REQUEST, RESPONSE> create(
      UrlAttributesGetter<REQUEST> getter) {
    return new UrlAttributesExtractor<>(getter);
  }

  private final InternalUrlAttributesExtractor<REQUEST> internalExtractor;

  UrlAttributesExtractor(UrlAttributesGetter<REQUEST> getter) {
    // the UrlAttributesExtractor will always emit new semconv
    internalExtractor =
        new InternalUrlAttributesExtractor<>(
            getter,
            /* alternateSchemeProvider= */ request -> null,
            /* emitStableUrlAttributes= */ true,
            /* emitOldHttpAttributes= */ false);
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
