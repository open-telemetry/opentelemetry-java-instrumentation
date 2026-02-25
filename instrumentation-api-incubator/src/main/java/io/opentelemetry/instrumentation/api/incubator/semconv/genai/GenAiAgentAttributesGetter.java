/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

public interface GenAiAgentAttributesGetter<REQUEST, RESPONSE> {

  String getName(REQUEST request);

  @Nullable
  String getDescription(REQUEST request);

  @Nullable
  String getId(REQUEST request);

  @Nullable
  String getDataSourceId(REQUEST request);
}
