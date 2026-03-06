/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai.incubator;

import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import java.util.List;

public final class GenAiIncubatingAttributes {

  public static final AttributeKey<String> GEN_AI_OPERATION_NAME =
      stringKey("gen_ai.operation.name");
  public static final AttributeKey<List<String>> GEN_AI_REQUEST_ENCODING_FORMATS =
      stringArrayKey("gen_ai.request.encoding_formats");
  public static final AttributeKey<Double> GEN_AI_REQUEST_FREQUENCY_PENALTY =
      doubleKey("gen_ai.request.frequency_penalty");
  public static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS =
      longKey("gen_ai.request.max_tokens");
  public static final AttributeKey<String> GEN_AI_REQUEST_MODEL = stringKey("gen_ai.request.model");
  public static final AttributeKey<Double> GEN_AI_REQUEST_PRESENCE_PENALTY =
      doubleKey("gen_ai.request.presence_penalty");
  public static final AttributeKey<Long> GEN_AI_REQUEST_SEED = longKey("gen_ai.request.seed");
  public static final AttributeKey<List<String>> GEN_AI_REQUEST_STOP_SEQUENCES =
      stringArrayKey("gen_ai.request.stop_sequences");
  public static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE =
      doubleKey("gen_ai.request.temperature");
  public static final AttributeKey<Double> GEN_AI_REQUEST_TOP_K = doubleKey("gen_ai.request.top_k");
  public static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P = doubleKey("gen_ai.request.top_p");
  public static final AttributeKey<List<String>> GEN_AI_RESPONSE_FINISH_REASONS =
      stringArrayKey("gen_ai.response.finish_reasons");
  public static final AttributeKey<String> GEN_AI_RESPONSE_ID = stringKey("gen_ai.response.id");
  public static final AttributeKey<String> GEN_AI_RESPONSE_MODEL =
      stringKey("gen_ai.response.model");
  public static final AttributeKey<String> GEN_AI_PROVIDER_NAME = stringKey("gen_ai.provider.name");
  public static final AttributeKey<String> GEN_AI_CONVERSATION_ID =
      stringKey("gen_ai.conversation.id");
  public static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS =
      longKey("gen_ai.usage.input_tokens");
  public static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS =
      longKey("gen_ai.usage.output_tokens");
  public static final AttributeKey<Long> GEN_AI_REQUEST_CHOICE_COUNT =
      longKey("gen_ai.request.choice.count");
  public static final AttributeKey<String> GEN_AI_OUTPUT_TYPE = stringKey("gen_ai.output.type");
  public static final AttributeKey<String> GEN_AI_SYSTEM_INSTRUCTIONS =
      stringKey("gen_ai.system_instructions");
  public static final AttributeKey<String> GEN_AI_INPUT_MESSAGES =
      stringKey("gen_ai.input.messages");
  public static final AttributeKey<String> GEN_AI_OUTPUT_MESSAGES =
      stringKey("gen_ai.output.messages");
  public static final AttributeKey<String> GEN_AI_TOOL_DEFINITIONS =
      stringKey("gen_ai.tool.definitions");

  public static class GenAiOperationNameIncubatingValues {
    public static final String CHAT = "chat";
    public static final String CREATE_AGENT = "create_agent";
    public static final String EMBEDDINGS = "embeddings";
    public static final String EXECUTE_TOOL = "execute_tool";
    public static final String GENERATE_CONTENT = "generate_content";
    public static final String INVOKE_AGENT = "invoke_agent";
    public static final String TEXT_COMPLETION = "text_completion";
  }

  public static class GenAiProviderNameIncubatingValues {
    public static final String ANTHROPIC = "anthropic";
    public static final String AWS_BEDROCK = "aws.bedrock";
    public static final String AZURE_AI_INFERENCE = "azure.ai.inference";
    public static final String AZURE_AI_OPENAI = "azure.ai.openai";
    public static final String COHERE = "cohere";
    public static final String DEEPSEEK = "deepseek";
    public static final String GCP_GEMINI = "gcp.gemini";
    public static final String GCP_GEN_AI = "gcp.gen_ai";
    public static final String GCP_VERTEX_AI = "gcp.vertex_ai";
    public static final String GROQ = "groq";
    public static final String IBM_WATSONX_AI = "ibm.watsonx.ai";
    public static final String MISTRAL_AI = "mistral_ai";
    public static final String OPENAI = "openai";
    public static final String PERPLEXITY = "perplexity";
    public static final String X_AI = "x_ai";
    public static final String DASHSCOPE = "dashscope";
  }

  public static class GenAiEventName {
    public static final String GEN_AI_CLIENT_INFERENCE_OPERATION_DETAILS =
        "gen_ai.client.inference.operation.details";
  }
}
