/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboNetClientAttributesGetter;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboNetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

/** A builder of {@link DubboTracing}. */
public final class DubboTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dubbo-2.7";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private final List<AttributesExtractor<DubboRequest, Result>> attributesExtractors =
      new ArrayList<>();

  DubboTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  public void setPeerService(String peerService) {
    this.peerService = peerService;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public DubboTracingBuilder addAttributesExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /** Returns a new {@link DubboTracing} with the settings of this {@link DubboTracingBuilder}. */
  public DubboTracing build() {
    DubboRpcAttributesExtractor rpcAttributesExtractor = new DubboRpcAttributesExtractor();
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesExtractor);

    DubboNetClientAttributesGetter netClientAttributesGetter = new DubboNetClientAttributesGetter();
    NetClientAttributesExtractor<DubboRequest, Result> netClientAttributesExtractor =
        NetClientAttributesExtractor.create(netClientAttributesGetter);

    InstrumenterBuilder<DubboRequest, Result> serverInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor);
    InstrumenterBuilder<DubboRequest, Result> clientInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor);

    Stream.of(serverInstrumenterBuilder, clientInstrumenterBuilder)
        .forEach(
            instrumenter ->
                instrumenter
                    .addAttributesExtractors(rpcAttributesExtractor)
                    .addAttributesExtractors(attributesExtractors));

    serverInstrumenterBuilder.addAttributesExtractor(new DubboNetServerAttributesExtractor());
    clientInstrumenterBuilder.addAttributesExtractor(netClientAttributesExtractor);

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    } else {
      clientInstrumenterBuilder.addAttributesExtractor(
          PeerServiceAttributesExtractor.create(netClientAttributesGetter));
    }

    return new DubboTracing(
        serverInstrumenterBuilder.newServerInstrumenter(DubboHeadersGetter.INSTANCE),
        clientInstrumenterBuilder.newClientInstrumenter(DubboHeadersSetter.INSTANCE));
  }
}
