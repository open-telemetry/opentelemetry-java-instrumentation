/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.thrift.common.client.ThriftClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.thrift.common.server.ThriftServerNetworkAttributesGetter;

public final class ThriftInstrumenterFactory {

  public static Instrumenter<ThriftRequest, Integer> clientInstrumenter(
      String instrumentationName) {
    ThriftClientNetworkAttributesGetter netClientAttributesGetter =
        new ThriftClientNetworkAttributesGetter();
    ThriftRpcAttributesGetter rpcAttributesGetter = ThriftRpcAttributesGetter.INSTANCE;
    return Instrumenter.<ThriftRequest, Integer>builder(
            GlobalOpenTelemetry.get(), instrumentationName, new ThriftSpanNameExtractor())
        .setSpanStatusExtractor(ThriftSpanStatusExtractor.INSTANCE)
        .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
        .addOperationMetrics(RpcClientMetrics.get())
        .buildClientInstrumenter(ThriftHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ThriftRequest, Integer> serverInstrumenter(
      String instrumentationName) {
    ThriftServerNetworkAttributesGetter netServerAttributesGetter =
        new ThriftServerNetworkAttributesGetter();
    ThriftRpcAttributesGetter rpcAttributesGetter = ThriftRpcAttributesGetter.INSTANCE;
    return Instrumenter.<ThriftRequest, Integer>builder(
            GlobalOpenTelemetry.get(), instrumentationName, new ThriftSpanNameExtractor())
        .setSpanStatusExtractor(ThriftSpanStatusExtractor.INSTANCE)
        .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
        .addOperationMetrics(RpcServerMetrics.get())
        .buildServerInstrumenter(ThriftHeaderGetter.INSTANCE);
  }

  private ThriftInstrumenterFactory() {}
}
