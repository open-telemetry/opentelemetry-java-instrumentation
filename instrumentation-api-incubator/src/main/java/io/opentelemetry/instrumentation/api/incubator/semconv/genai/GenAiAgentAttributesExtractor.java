/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/">
 * GenAI agent attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link GenAiAgentAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class GenAiAgentAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from GenAiIncubatingAttributes
  private static final AttributeKey<String> GEN_AI_AGENT_DESCRIPTION =
      stringKey("gen_ai.agent.description");
  private static final AttributeKey<String> GEN_AI_AGENT_ID = stringKey("gen_ai.agent.id");
  private static final AttributeKey<String> GEN_AI_AGENT_NAME = stringKey("gen_ai.agent.name");
  private static final AttributeKey<String> GEN_AI_DATA_SOURCE_ID =
      stringKey("gen_ai.data_source.id");

  /** Creates the GenAI agent attributes extractor. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      GenAiAgentAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    return new GenAiAgentAttributesExtractor<>(attributesGetter);
  }

  private final GenAiAgentAttributesGetter<REQUEST, RESPONSE> getter;

  private GenAiAgentAttributesExtractor(GenAiAgentAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, GEN_AI_AGENT_DESCRIPTION, getter.getDescription(request));
    internalSet(attributes, GEN_AI_AGENT_ID, getter.getId(request));
    internalSet(attributes, GEN_AI_AGENT_NAME, getter.getName(request));
    internalSet(attributes, GEN_AI_DATA_SOURCE_ID, getter.getDataSourceId(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
