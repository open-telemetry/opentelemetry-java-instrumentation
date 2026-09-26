/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import javax.annotation.Nullable;
import org.assertj.core.api.ThrowingConsumer;

/**
 * Assertions on the {@link InstrumentationScopeInfo} of a {@link SpanData}, {@link MetricData} or
 * {@link LogRecordData}, intended to be passed to {@code satisfies(...)}:
 *
 * <pre>{@code
 * span.hasName("GET").satisfies(hasScopeSchemaUrl(SchemaUrls.V1_37_0));
 * }</pre>
 */
public final class InstrumentationScopeAssertions {

  /** Asserts that the instrumentation scope has the given name. */
  public static <T> ThrowingConsumer<T> hasScopeName(String name) {
    return data ->
        assertThat(scopeOf(data).getName()).as("instrumentation scope name").isEqualTo(name);
  }

  /** Asserts that the instrumentation scope has the given version, or no version if null. */
  public static <T> ThrowingConsumer<T> hasScopeVersion(@Nullable String version) {
    return data ->
        assertThat(scopeOf(data).getVersion())
            .as("instrumentation scope version")
            .isEqualTo(version);
  }

  /** Asserts that the instrumentation scope has the given schema URL, or no schema URL if null. */
  public static <T> ThrowingConsumer<T> hasScopeSchemaUrl(@Nullable String schemaUrl) {
    return data ->
        assertThat(scopeOf(data).getSchemaUrl())
            .as("instrumentation scope schema url")
            .isEqualTo(schemaUrl);
  }

  private static InstrumentationScopeInfo scopeOf(Object data) {
    if (data instanceof SpanData) {
      return ((SpanData) data).getInstrumentationScopeInfo();
    }
    if (data instanceof MetricData) {
      return ((MetricData) data).getInstrumentationScopeInfo();
    }
    if (data instanceof LogRecordData) {
      return ((LogRecordData) data).getInstrumentationScopeInfo();
    }
    throw new IllegalArgumentException(
        "Expected SpanData, MetricData or LogRecordData but got: "
            + (data == null ? null : data.getClass().getName()));
  }

  private InstrumentationScopeAssertions() {}
}
