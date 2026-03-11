/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Extended GenAI semantic convention attributes that are not yet available in the standard
 * opentelemetry-semconv package. These follow the OTel GenAI semantic conventions (incubating).
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/">gen-ai-spans</a>
 */
public final class GenAiExtendedAttributes {

  // Agent attributes
  public static final AttributeKey<String> GEN_AI_AGENT_ID = stringKey("gen_ai.agent.id");
  public static final AttributeKey<String> GEN_AI_AGENT_NAME = stringKey("gen_ai.agent.name");
  public static final AttributeKey<String> GEN_AI_AGENT_DESCRIPTION =
      stringKey("gen_ai.agent.description");
  public static final AttributeKey<String> GEN_AI_DATA_SOURCE_ID =
      stringKey("gen_ai.data_source.id");
  public static final AttributeKey<String> GEN_AI_CONVERSATION_ID =
      stringKey("gen_ai.conversation.id");

  // Tool attributes
  public static final AttributeKey<String> GEN_AI_TOOL_CALL_ID = stringKey("gen_ai.tool.call.id");
  public static final AttributeKey<String> GEN_AI_TOOL_DESCRIPTION =
      stringKey("gen_ai.tool.description");
  public static final AttributeKey<String> GEN_AI_TOOL_NAME = stringKey("gen_ai.tool.name");
  public static final AttributeKey<String> GEN_AI_TOOL_TYPE = stringKey("gen_ai.tool.type");
  public static final AttributeKey<String> GEN_AI_TOOL_CALL_ARGUMENTS =
      stringKey("gen_ai.tool.call.arguments");
  public static final AttributeKey<String> GEN_AI_TOOL_CALL_RESULT =
      stringKey("gen_ai.tool.call.result");

  // Retrieve attributes
  public static final AttributeKey<String> GEN_AI_RETRIEVAL_QUERY =
      stringKey("gen_ai.retrieval.query");
  public static final AttributeKey<String> GEN_AI_RETRIEVAL_DOCUMENTS =
      stringKey("gen_ai.retrieval.documents");

  // Rerank attributes
  public static final AttributeKey<Long> GEN_AI_RERANK_DOCUMENTS_COUNT =
      longKey("gen_ai.rerank.documents.count");
  public static final AttributeKey<String> GEN_AI_RERANK_INPUT_DOCUMENTS =
      stringKey("gen_ai.rerank.input_documents");
  public static final AttributeKey<String> GEN_AI_RERANK_OUTPUT_DOCUMENTS =
      stringKey("gen_ai.rerank.output_documents");

  // Memory attributes
  public static final AttributeKey<String> GEN_AI_MEMORY_OPERATION =
      stringKey("gen_ai.memory.operation");
  public static final AttributeKey<String> GEN_AI_MEMORY_USER_ID =
      stringKey("gen_ai.memory.user_id");
  public static final AttributeKey<String> GEN_AI_MEMORY_AGENT_ID =
      stringKey("gen_ai.memory.agent_id");
  public static final AttributeKey<String> GEN_AI_MEMORY_RUN_ID = stringKey("gen_ai.memory.run_id");
  public static final AttributeKey<String> GEN_AI_MEMORY_ID = stringKey("gen_ai.memory.id");
  public static final AttributeKey<Long> GEN_AI_MEMORY_TOP_K = longKey("gen_ai.memory.top_k");
  public static final AttributeKey<String> GEN_AI_MEMORY_MEMORY_TYPE =
      stringKey("gen_ai.memory.memory_type");
  public static final AttributeKey<Double> GEN_AI_MEMORY_THRESHOLD =
      doubleKey("gen_ai.memory.threshold");
  public static final AttributeKey<Boolean> GEN_AI_MEMORY_RERANK =
      booleanKey("gen_ai.memory.rerank");
  public static final AttributeKey<String> GEN_AI_MEMORY_INPUT_MESSAGES =
      stringKey("gen_ai.memory.input.messages");
  public static final AttributeKey<String> GEN_AI_MEMORY_OUTPUT_MESSAGES =
      stringKey("gen_ai.memory.output.messages");

  // Additional usage attributes
  public static final AttributeKey<Long> GEN_AI_USAGE_TOTAL_TOKENS =
      longKey("gen_ai.usage.total_tokens");
  public static final AttributeKey<String> GEN_AI_SPAN_KIND = stringKey("gen_ai.span.kind");
  public static final AttributeKey<Long> GEN_AI_RESPONSE_TIME_TO_FIRST_TOKEN =
      longKey("gen_ai.response.time_to_first_token");

  // Embeddings attributes
  public static final AttributeKey<Long> GEN_AI_EMBEDDINGS_DIMENSION_COUNT =
      longKey("gen_ai.embeddings.dimension.count");

  // Tool definitions and messages (content attributes)
  public static final AttributeKey<String> GEN_AI_TOOL_DEFINITIONS =
      stringKey("gen_ai.tool.definitions");
  public static final AttributeKey<String> GEN_AI_INPUT_MESSAGES =
      stringKey("gen_ai.input.messages");
  public static final AttributeKey<String> GEN_AI_OUTPUT_MESSAGES =
      stringKey("gen_ai.output.messages");
  public static final AttributeKey<String> GEN_AI_SYSTEM_INSTRUCTIONS =
      stringKey("gen_ai.system_instructions");

  /** GenAI operation name values for extended operation types. */
  public static final class GenAiOperationNameValues {
    public static final String CHAT = "chat";
    public static final String TEXT_COMPLETION = "text_completion";
    public static final String EMBEDDINGS = "embeddings";
    public static final String CREATE_AGENT = "create_agent";
    public static final String INVOKE_AGENT = "invoke_agent";
    public static final String EXECUTE_TOOL = "execute_tool";
    public static final String RETRIEVE_DOCUMENTS = "retrieve_documents";
    public static final String RERANK_DOCUMENTS = "rerank_documents";
    public static final String MEMORY_OPERATION = "memory_operation";

    private GenAiOperationNameValues() {}
  }

  /** GenAI span kind values for classifying operation types. */
  public static final class GenAiSpanKindValues {
    public static final String LLM = "LLM";
    public static final String AGENT = "AGENT";
    public static final String TOOL = "TOOL";
    public static final String EMBEDDING = "EMBEDDING";
    public static final String RETRIEVER = "RETRIEVER";
    public static final String RERANKER = "RERANKER";
    public static final String MEMORY = "MEMORY";

    private GenAiSpanKindValues() {}
  }

  private GenAiExtendedAttributes() {}
}
