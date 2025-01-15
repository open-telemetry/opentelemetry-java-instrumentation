/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jsonrpc4j.v1_3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.ArrayList;
import java.util.List;

public class JsonRpcTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jsonrpc4j-1.3";

  private final OpenTelemetry openTelemetry;

  private final List<
          AttributesExtractor<? super JsonRpcClientRequest, ? super JsonRpcClientResponse>>
      additionalClientExtractors = new ArrayList<>();
  private final List<
          AttributesExtractor<? super JsonRpcServerRequest, ? super JsonRpcServerResponse>>
      additionalServerExtractors = new ArrayList<>();

  JsonRpcTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an extra client-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public JsonRpcTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<? super JsonRpcClientRequest, ? super JsonRpcClientResponse>
          attributesExtractor) {
    additionalClientExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra server-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public JsonRpcTelemetryBuilder addServerAttributeExtractor(
      AttributesExtractor<? super JsonRpcServerRequest, ? super JsonRpcServerResponse>
          attributesExtractor) {
    additionalServerExtractors.add(attributesExtractor);
    return this;
  }

  public JsonRpcTelemetry build() {
    SpanNameExtractor<JsonRpcClientRequest> clientSpanNameExtractor =
        new JsonRpcClientSpanNameExtractor();
    SpanNameExtractor<JsonRpcServerRequest> serverSpanNameExtractor =
        new JsonRpcServerSpanNameExtractor();

    InstrumenterBuilder<JsonRpcClientRequest, JsonRpcClientResponse> clientInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, clientSpanNameExtractor);

    InstrumenterBuilder<JsonRpcServerRequest, JsonRpcServerResponse> serverInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor);

    JsonRpcServerAttributesGetter serverRpcAttributesGetter =
        JsonRpcServerAttributesGetter.INSTANCE;
    JsonRpcClientAttributesGetter clientRpcAttributesGetter =
        JsonRpcClientAttributesGetter.INSTANCE;

    clientInstrumenterBuilder
        .addAttributesExtractor(RpcClientAttributesExtractor.create(clientRpcAttributesGetter))
        .addAttributesExtractors(additionalClientExtractors)
        .addAttributesExtractor(new JsonRpcClientAttributesExtractor())
        .addOperationMetrics(RpcClientMetrics.get());

    serverInstrumenterBuilder
        .setSpanStatusExtractor(JsonRpcServerSpanStatusExtractor.INSTANCE)
        .addAttributesExtractor(RpcServerAttributesExtractor.create(serverRpcAttributesGetter))
        .addAttributesExtractor(new JsonRpcServerAttributesExtractor())
        .addAttributesExtractors(additionalServerExtractors)
        .addOperationMetrics(RpcServerMetrics.get());

    return new JsonRpcTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(JsonRpcServerRequestGetter.INSTANCE),
        clientInstrumenterBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient()),
        openTelemetry.getPropagators());
  }
}
