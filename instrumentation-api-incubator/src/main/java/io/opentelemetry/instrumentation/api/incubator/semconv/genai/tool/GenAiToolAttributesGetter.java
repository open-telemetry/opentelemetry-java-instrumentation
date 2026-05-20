/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.tool;

import io.opentelemetry.instrumentation.api.instrumenter.genai.GenAiOperationAttributesGetter;
import javax.annotation.Nullable;

public interface GenAiToolAttributesGetter<REQUEST, RESPONSE>
    extends GenAiOperationAttributesGetter<REQUEST, RESPONSE> {

  String getToolDescription(REQUEST request);

  String getToolName(REQUEST request);

  String getToolType(REQUEST request);

  @Nullable
  String getToolCallArguments(REQUEST request);

  @Nullable
  String getToolCallId(REQUEST request, RESPONSE response);

  @Nullable
  String getToolCallResult(REQUEST request, RESPONSE response);
}
