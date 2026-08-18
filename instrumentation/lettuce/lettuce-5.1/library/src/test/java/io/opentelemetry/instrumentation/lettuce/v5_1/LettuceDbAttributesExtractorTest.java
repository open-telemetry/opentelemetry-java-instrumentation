/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.REDIS;

import io.lettuce.core.tracing.Tracer;
import io.lettuce.core.tracing.Tracing;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// lettuce reports the Redis database index only from 6.5.0 through 7.0.x (through its db.namespace
// tracing tag); the tag is gone again by 7.1.0. So neither the pinned 5.1.0 nor latest deps
// populates it on a real command span.
class LettuceDbAttributesExtractorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void emitsLegacyDatabaseIndexFromTracingTagOnlyUnderOldSemconv() {
    Tracing tracing = LettuceTelemetry.create(testing.getOpenTelemetry()).createTracing();
    Tracer.Span span =
        tracing
            .getTracerProvider()
            .getTracer()
            .nextSpan()
            .name("GET")
            .tag("db.namespace", "1")
            .start();
    span.finish();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                spanData ->
                    spanData
                        .hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_NAME), "1"),
                            equalTo(maybeStable(DB_STATEMENT), "GET"),
                            equalTo(maybeStable(DB_OPERATION), "GET"),
                            equalTo(
                                DB_REDIS_DATABASE_INDEX, emitOldDatabaseSemconv() ? 1L : null))));
  }
}
