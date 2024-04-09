/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.semconv.incubating.PeerIncubatingAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

/** A builder of {@link DubboTelemetry}. */
public final class DubboTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dubbo-2.7";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private final List<AttributesExtractor<DubboRequest, Result>> attributesExtractors =
      new ArrayList<>();

  DubboTelemetryBuilder(OpenTelemetry openTelemetry) {
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
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Returns a new {@link DubboTelemetry} with the settings of this {@link DubboTelemetryBuilder}.
   */
  public DubboTelemetry build() {
    DubboRpcAttributesGetter rpcAttributesGetter = DubboRpcAttributesGetter.INSTANCE;
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);
    DubboClientNetworkAttributesGetter netClientAttributesGetter =
        new DubboClientNetworkAttributesGetter();
    DubboNetworkServerAttributesGetter netServerAttributesGetter =
        new DubboNetworkServerAttributesGetter();

    InstrumenterBuilder<DubboRequest, Result> serverInstrumenterBuilder =
        Instrumenter.<DubboRequest, Result>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    InstrumenterBuilder<DubboRequest, Result> clientInstrumenterBuilder =
        Instrumenter.<DubboRequest, Result>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractors(attributesExtractors);

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(PeerIncubatingAttributes.PEER_SERVICE, peerService));
    }

    return new DubboTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(DubboHeadersGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(DubboHeadersSetter.INSTANCE));
  }
}
