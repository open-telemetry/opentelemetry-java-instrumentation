package io.opentelemetry.instrumentation.api.incubator.semconv.genai.incubator;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

public class GenAiToolIncubatingAttributes {

  public static final AttributeKey<String> GEN_AI_TOOL_CALL_ID = stringKey("gen_ai.tool.call.id");
  public static final AttributeKey<String> GEN_AI_TOOL_DESCRIPTION = stringKey("gen_ai.tool.description");
  public static final AttributeKey<String> GEN_AI_TOOL_NAME = stringKey("gen_ai.tool.name");
  public static final AttributeKey<String> GEN_AI_TOOL_TYPE = stringKey("gen_ai.tool.type");
  public static final AttributeKey<String> GEN_AI_TOOL_CALL_ARGUMENTS = stringKey("gen_ai.tool.call.arguments");
  public static final AttributeKey<String> GEN_AI_TOOL_CALL_RESULT = stringKey("gen_ai.tool.call.result");
}
