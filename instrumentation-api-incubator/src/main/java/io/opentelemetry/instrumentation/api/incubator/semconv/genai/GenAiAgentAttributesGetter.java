/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import javax.annotation.Nullable;

/**
 * An interface for getting GenAI agent attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link GenAiAgentAttributesExtractor} to obtain the
 * various GenAI agent attributes in a type-generic way.
 */
public interface GenAiAgentAttributesGetter<REQUEST, RESPONSE>
    extends GenAiAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  String getName(REQUEST request);

  @Nullable
  String getDescription(REQUEST request);

  @Nullable
  String getId(REQUEST request);

  @Nullable
  String getDataSourceId(REQUEST request);
}
