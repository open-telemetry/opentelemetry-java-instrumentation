/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_REQUEST_MODEL;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_RESPONSE_MODEL;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_SYSTEM;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiClientMetrics.GEN_AI_TOKEN_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.semconv.ErrorAttributes;
import java.util.List;

final class GenAiMetricsAdvice {

  static final List<Double> CLIENT_OPERATION_DURATION_BUCKETS =
      unmodifiableList(
          asList(
              0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96,
              81.92));

  static final List<Long> CLIENT_TOKEN_USAGE_BUCKETS =
      unmodifiableList(
          asList(
              1L, 4L, 16L, 64L, 256L, 1024L, 4096L, 16384L, 65536L, 262144L, 1048576L, 4194304L,
              16777216L, 67108864L));

  static void applyClientTokenUsageAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                GEN_AI_OPERATION_NAME,
                GEN_AI_SYSTEM,
                GEN_AI_TOKEN_TYPE,
                GEN_AI_REQUEST_MODEL,
                SERVER_PORT,
                GEN_AI_RESPONSE_MODEL,
                SERVER_ADDRESS));
  }

  static void applyClientOperationDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                GEN_AI_OPERATION_NAME,
                GEN_AI_SYSTEM,
                ErrorAttributes.ERROR_TYPE,
                GEN_AI_REQUEST_MODEL,
                SERVER_PORT,
                GEN_AI_RESPONSE_MODEL,
                SERVER_ADDRESS));
  }

  private GenAiMetricsAdvice() {}
}
