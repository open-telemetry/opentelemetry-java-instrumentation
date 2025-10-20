/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

public interface GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  String getOperationName(REQUEST request);

  @Nullable
  String getOperationTarget(REQUEST request);
}
