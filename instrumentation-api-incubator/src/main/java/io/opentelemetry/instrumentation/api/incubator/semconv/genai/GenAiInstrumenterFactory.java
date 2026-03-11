/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.genai;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A factory for building {@link Instrumenter} instances tailored to various GenAI operation types
 * (Chat, Agent, Tool, Embedding, Retrieve, Rerank, Memory).
 *
 * <p>This factory can be used by both <b>javaagent auto-instrumentation</b> and <b>SDK library
 * instrumentation</b>.
 *
 * <h3>SDK usage example:</h3>
 *
 * <pre>{@code
 * OpenTelemetry otel = GlobalOpenTelemetry.get();
 *
 * GenAiInstrumenterFactory factory = GenAiInstrumenterFactory.builder(otel, "my-app")
 *     .setCaptureMessageContent(true)
 *     .build();
 *
 * Instrumenter<MyReq, MyResp> chatInstrumenter =
 *     factory.createChatInstrumenter(new MyChatAttributesGetter());
 *
 * // ... use chatInstrumenter.shouldStart / start / end
 * }</pre>
 *
 * <h3>Javaagent usage example:</h3>
 *
 * <pre>{@code
 * GenAiInstrumenterFactory factory = GenAiInstrumenterFactory.builder(
 *         GlobalOpenTelemetry.get(), "io.opentelemetry.spring-ai-1.0")
 *     .setCaptureMessageContent(agentConfig.getBoolean("otel.genai.capture-message-content"))
 *     .build();
 *
 * Instrumenter<AgentRequest, AgentResponse> agentInstrumenter =
 *     factory.createInvokeAgentInstrumenter(new SpringAiAgentGetter());
 * }</pre>
 */
public final class GenAiInstrumenterFactory {

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private final boolean captureMessageContent;

  private GenAiInstrumenterFactory(Builder builder) {
    this.openTelemetry = builder.openTelemetry;
    this.instrumentationName = builder.instrumentationName;
    this.captureMessageContent = builder.captureMessageContent;
  }

  /** Creates a new {@link Builder}. */
  public static Builder builder(OpenTelemetry openTelemetry, String instrumentationName) {
    return new Builder(openTelemetry, instrumentationName);
  }

  /**
   * Creates a convenience factory instance without additional configuration. Equivalent to {@code
   * builder(openTelemetry, instrumentationName).build()}.
   */
  public static GenAiInstrumenterFactory create(
      OpenTelemetry openTelemetry, String instrumentationName, boolean captureMessageContent) {
    return builder(openTelemetry, instrumentationName)
        .setCaptureMessageContent(captureMessageContent)
        .build();
  }

  // ---------------------------------------------------------------------------
  // Chat / LLM
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for GenAI Chat (LLM) operations. Span name: {@code chat
   * <model>} or {@code text_completion <model>}. Default SpanKind: CLIENT.
   *
   * @param getter the chat attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createChatInstrumenter(
      GenAiAttributesGetter<REQUEST, RESPONSE> getter) {
    return createChatInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for GenAI Chat (LLM) operations with additional extractors.
   *
   * @param getter the chat attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createChatInstrumenter(
      GenAiAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry, instrumentationName, GenAiSpanNameExtractor.create(getter))
            .addAttributesExtractor(GenAiAttributesExtractor.create(getter))
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(GenAiClientMetrics.get());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  // ---------------------------------------------------------------------------
  // Embedding
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for GenAI Embedding operations. Reuses the same {@link
   * GenAiAttributesGetter} as Chat (since embeddings share model/provider/token attributes). Span
   * name: {@code embeddings <model>}. Default SpanKind: CLIENT.
   *
   * @param getter the embedding attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createEmbeddingInstrumenter(
      GenAiAttributesGetter<REQUEST, RESPONSE> getter) {
    return createEmbeddingInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for GenAI Embedding operations with additional extractors.
   *
   * @param getter the embedding attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createEmbeddingInstrumenter(
      GenAiAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry, instrumentationName, GenAiSpanNameExtractor.create(getter))
            .addAttributesExtractor(GenAiAttributesExtractor.create(getter))
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(GenAiClientMetrics.get());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  // ---------------------------------------------------------------------------
  // Invoke Agent
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for invoke_agent operations. Span name: {@code invoke_agent
   * <agent_name>}. Default SpanKind: INTERNAL.
   *
   * @param getter the agent attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createInvokeAgentInstrumenter(
      GenAiAgentAttributesGetter<REQUEST, RESPONSE> getter) {
    return createInvokeAgentInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for invoke_agent operations with additional extractors.
   *
   * @param getter the agent attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createInvokeAgentInstrumenter(
      GenAiAgentAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    return createInvokeAgentInstrumenter(getter, null, additionalExtractors);
  }

  /**
   * Creates an {@link Instrumenter} for invoke_agent operations with inference attributes. When
   * {@code inferenceGetter} is provided, the agent span will also include GenAI inference attributes
   * (model, temperature, tokens, etc.) and client metrics.
   *
   * @param agentGetter the agent attributes getter
   * @param inferenceGetter optional inference attributes getter for model/token attributes
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createInvokeAgentInstrumenter(
      GenAiAgentAttributesGetter<REQUEST, RESPONSE> agentGetter,
      @Nullable GenAiAttributesGetter<REQUEST, RESPONSE> inferenceGetter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry,
                instrumentationName,
                GenAiAgentSpanNameExtractor.forInvokeAgent(agentGetter))
            .addAttributesExtractor(GenAiAgentAttributesExtractor.create(agentGetter))
            .addAttributesExtractors(additionalExtractors);
    if (inferenceGetter != null) {
      builder.addAttributesExtractor(
          GenAiAttributesExtractor.builder(inferenceGetter)
              .setCaptureMessageContent(captureMessageContent)
              .build());
      builder.addOperationMetrics(GenAiClientMetrics.get());
    }
    return builder.buildInstrumenter();
  }

  // ---------------------------------------------------------------------------
  // Create Agent
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for create_agent operations. Span name: {@code create_agent
   * <agent_name>}. Default SpanKind: INTERNAL.
   *
   * @param getter the agent attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createCreateAgentInstrumenter(
      GenAiAgentAttributesGetter<REQUEST, RESPONSE> getter) {
    return createCreateAgentInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for create_agent operations with additional extractors.
   *
   * @param getter the agent attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createCreateAgentInstrumenter(
      GenAiAgentAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry,
                instrumentationName,
                GenAiAgentSpanNameExtractor.forCreateAgent(getter))
            .addAttributesExtractor(GenAiAgentAttributesExtractor.create(getter))
            .addAttributesExtractors(additionalExtractors);
    return builder.buildInstrumenter();
  }

  // ---------------------------------------------------------------------------
  // Execute Tool
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for execute_tool operations. Span name: {@code execute_tool
   * <tool_name>}. Default SpanKind: INTERNAL.
   *
   * @param getter the tool attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createToolInstrumenter(
      GenAiToolAttributesGetter<REQUEST, RESPONSE> getter) {
    return createToolInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for execute_tool operations with additional extractors.
   *
   * @param getter the tool attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createToolInstrumenter(
      GenAiToolAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry, instrumentationName, GenAiToolSpanNameExtractor.create(getter))
            .addAttributesExtractor(
                GenAiToolAttributesExtractor.create(getter, captureMessageContent))
            .addAttributesExtractors(additionalExtractors);
    return builder.buildInstrumenter();
  }

  // ---------------------------------------------------------------------------
  // Retrieve Documents
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for retrieve_documents operations. Span name: {@code
   * retrieve_documents}. Default SpanKind: INTERNAL.
   *
   * @param getter the retrieve attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createRetrieveInstrumenter(
      GenAiRetrieveAttributesGetter<REQUEST, RESPONSE> getter) {
    return createRetrieveInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for retrieve_documents operations with additional extractors.
   *
   * @param getter the retrieve attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createRetrieveInstrumenter(
      GenAiRetrieveAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry,
                instrumentationName,
                request -> GenAiExtendedAttributes.GenAiOperationNameValues.RETRIEVE_DOCUMENTS)
            .addAttributesExtractor(
                GenAiRetrieveAttributesExtractor.create(getter, captureMessageContent))
            .addAttributesExtractors(additionalExtractors);
    return builder.buildInstrumenter();
  }

  // ---------------------------------------------------------------------------
  // Rerank Documents
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for rerank_documents operations. Span name: {@code
   * rerank_documents <model>}. Default SpanKind: CLIENT.
   *
   * @param getter the rerank attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createRerankInstrumenter(
      GenAiRerankAttributesGetter<REQUEST, RESPONSE> getter) {
    return createRerankInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for rerank_documents operations with additional extractors.
   *
   * @param getter the rerank attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createRerankInstrumenter(
      GenAiRerankAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry,
                instrumentationName,
                request -> {
                  String model = getter.getRequestModel(request);
                  if (model == null) {
                    return GenAiExtendedAttributes.GenAiOperationNameValues.RERANK_DOCUMENTS;
                  }
                  return GenAiExtendedAttributes.GenAiOperationNameValues.RERANK_DOCUMENTS
                      + ' '
                      + model;
                })
            .addAttributesExtractor(
                GenAiRerankAttributesExtractor.create(getter, captureMessageContent))
            .addAttributesExtractors(additionalExtractors)
            .addOperationMetrics(GenAiClientMetrics.get());
    return builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  // ---------------------------------------------------------------------------
  // Memory Operation
  // ---------------------------------------------------------------------------

  /**
   * Creates an {@link Instrumenter} for memory operations. Span name: {@code memory_operation
   * <operation_type>}. Default SpanKind: INTERNAL.
   *
   * @param getter the memory attributes getter
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createMemoryInstrumenter(
      GenAiMemoryAttributesGetter<REQUEST, RESPONSE> getter) {
    return createMemoryInstrumenter(getter, new ArrayList<>());
  }

  /**
   * Creates an {@link Instrumenter} for memory operations with additional extractors.
   *
   * @param getter the memory attributes getter
   * @param additionalExtractors additional attribute extractors
   */
  public <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> createMemoryInstrumenter(
      GenAiMemoryAttributesGetter<REQUEST, RESPONSE> getter,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors) {
    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry, instrumentationName, GenAiMemorySpanNameExtractor.create(getter))
            .addAttributesExtractor(
                GenAiMemoryAttributesExtractor.create(getter, captureMessageContent))
            .addAttributesExtractors(additionalExtractors);
    return builder.buildInstrumenter();
  }

  // ---------------------------------------------------------------------------
  // Accessors
  // ---------------------------------------------------------------------------

  /** Returns the OpenTelemetry instance used by this factory. */
  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  /** Returns the instrumentation name. */
  public String getInstrumentationName() {
    return instrumentationName;
  }

  /** Returns whether message content capture is enabled. */
  public boolean isCaptureMessageContent() {
    return captureMessageContent;
  }

  // ---------------------------------------------------------------------------
  // Builder
  // ---------------------------------------------------------------------------

  /** Builder for {@link GenAiInstrumenterFactory}. */
  public static final class Builder {
    private final OpenTelemetry openTelemetry;
    private final String instrumentationName;
    private boolean captureMessageContent;

    private Builder(OpenTelemetry openTelemetry, String instrumentationName) {
      this.openTelemetry = requireNonNull(openTelemetry, "openTelemetry");
      this.instrumentationName = requireNonNull(instrumentationName, "instrumentationName");
    }

    /**
     * Sets whether sensitive data (message content, tool arguments/results, documents, etc.) should
     * be captured. Default is {@code false}.
     *
     * <p>Note that full content can have data privacy and size concerns and care should be taken
     * when enabling this.
     */
    @CanIgnoreReturnValue
    public Builder setCaptureMessageContent(boolean captureMessageContent) {
      this.captureMessageContent = captureMessageContent;
      return this;
    }

    /** Returns a new {@link GenAiInstrumenterFactory} with the settings of this builder. */
    public GenAiInstrumenterFactory build() {
      return new GenAiInstrumenterFactory(this);
    }
  }
}
