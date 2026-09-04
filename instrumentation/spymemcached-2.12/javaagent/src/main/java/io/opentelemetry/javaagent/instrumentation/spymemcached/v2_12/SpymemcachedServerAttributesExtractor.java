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
      ServerAttributesExtractor.create(new SpymemcachedAttributesGetter());

  @Override
  public void onStart(AttributesBuilder attributes, Context context, SpymemcachedRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SpymemcachedRequest request,
      @Nullable Object response,
      @Nullable Throwable error) {
    if (!emitStableDatabaseSemconv()) {
      delegate.onStart(attributes, context, request);
    }
  }
}
