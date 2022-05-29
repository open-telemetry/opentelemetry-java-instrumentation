/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.Response;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesExtractor;

public class OpenfeignInstrumentationSingletons {

  private OpenfeignInstrumentationSingletons(){}

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.openfeign-9.2";
  private static final Instrumenter<ExecuteAndDecodeRequest, Response> INSTRUMENTER;

  static {

    INSTRUMENTER =
        Instrumenter.<ExecuteAndDecodeRequest, Response>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME,
                new OpenfeignSpanNameExtractor())
            .addAttributesExtractor(
                RpcClientAttributesExtractor.create(OpenfeignAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                HttpClientAttributesExtractor.create(OpenfeignAttributesGetter.INSTANCE))
            .addAttributesExtractor(
                NetClientAttributesExtractor.create(OpenfeignAttributesGetter.INSTANCE))
            .newClientInstrumenter(OpenfeignTextMapSetter.INSTANCE);
  }

  public static Instrumenter<ExecuteAndDecodeRequest, Response> instrumenter() {
    return INSTRUMENTER;
  }

}
