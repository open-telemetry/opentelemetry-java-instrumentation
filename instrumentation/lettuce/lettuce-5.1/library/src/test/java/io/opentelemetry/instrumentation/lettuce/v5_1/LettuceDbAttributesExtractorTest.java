/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import org.junit.jupiter.api.Test;

// lettuce reports the Redis database index only from 6.5.0 through 7.0.x (through its db.namespace
// tracing tag); the tag is gone again by 7.1.0. So neither the pinned 5.1.0 nor latest deps
// populates it on a real command span, and these tests drive the extractor directly to cover the
// legacy attribute across semconv modes.
class LettuceDbAttributesExtractorTest {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void emitsLegacyDatabaseIndexOnlyUnderOldSemconv() {
    LettuceRequest request = new LettuceRequest(RedisCommandSanitizer.create(true));
    request.setDatabaseIndex(1);

    AttributesBuilder builder = Attributes.builder();
    new LettuceDbAttributesExtractor().onStart(builder, Context.root(), request);
    Attributes attributes = builder.build();

    if (emitOldDatabaseSemconv()) {
      assertThat(attributes).hasSize(1).containsEntry(DB_REDIS_DATABASE_INDEX, 1L);
    } else {
      assertThat(attributes).isEmpty();
    }
  }

  @Test
  void doesNotEmitWhenDatabaseIndexAbsent() {
    LettuceRequest request = new LettuceRequest(RedisCommandSanitizer.create(true));

    AttributesBuilder builder = Attributes.builder();
    new LettuceDbAttributesExtractor().onStart(builder, Context.root(), request);

    assertThat(builder.build()).isEmpty();
  }
}
