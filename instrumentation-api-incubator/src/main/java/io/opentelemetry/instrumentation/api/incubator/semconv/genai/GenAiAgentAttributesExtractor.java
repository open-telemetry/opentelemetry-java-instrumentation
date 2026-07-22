/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.incubator.semconv.genai.GenAiAttributesExtractor.GEN_AI_OPERATION_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/">GenAI agent
 * attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link GenAiAgentAttributesGetter} for individual
 * attribute extraction from request/response objects.
 *
 * <p>Note: this extractor does not emit {@code gen_ai.provider.name}; the surrounding instrumenter
 * is expected to attach it (e.g. via a separate {@link AttributesExtractor}).
 */
public final class GenAiAgentAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // copied from GenAiIncubatingAttributes
  private static final AttributeKey<String> GEN_AI_AGENT_DESCRIPTION =
      stringKey("gen_ai.agent.description");
  private static final AttributeKey<String> GEN_AI_AGENT_ID = stringKey("gen_ai.agent.id");
  private static final AttributeKey<String> GEN_AI_AGENT_NAME = stringKey("gen_ai.agent.name");
  private static final AttributeKey<String> GEN_AI_AGENT_VERSION =
      stringKey("gen_ai.agent.version");

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
    attributes.put(GEN_AI_OPERATION_NAME, getter.getOperationName(request));
    attributes.put(GEN_AI_AGENT_ID, getter.getAgentId(request));
    attributes.put(GEN_AI_AGENT_NAME, getter.getAgentName(request));
    attributes.put(GEN_AI_AGENT_DESCRIPTION, getter.getAgentDescription(request));
    attributes.put(GEN_AI_AGENT_VERSION, getter.getAgentVersion(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
