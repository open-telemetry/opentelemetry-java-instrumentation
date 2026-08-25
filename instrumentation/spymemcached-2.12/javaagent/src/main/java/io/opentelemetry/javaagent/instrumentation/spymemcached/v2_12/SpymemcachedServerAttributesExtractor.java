/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import javax.annotation.Nullable;

class SpymemcachedServerAttributesExtractor
    implements AttributesExtractor<SpymemcachedRequest, Object> {

  private final ServerAttributesExtractor<SpymemcachedRequest, Void> delegate =
      ServerAttributesExtractor.create(new SpymemcachedServerAttributesGetter());

  @Override
  public void onStart(AttributesBuilder attributes, Context context, SpymemcachedRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SpymemcachedRequest request,
      @Nullable Object object,
      @Nullable Throwable error) {
    // The handling node is only known after dispatch. A captured configured target already
    // describes the server under stable semantic conventions.
    if (!emitStableDatabaseSemconv() || request.getServerTarget() == null) {
      delegate.onStart(attributes, context, request);
    }
  }
}
