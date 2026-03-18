/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboInternalHelper;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Result;

class DubboSingletons {
  private static final Filter clientFilter;
  private static final Filter serverFilter;

  @Nullable public static final Instrumenter<DubboRequest, Result> SERVER_INSTRUMENTER;

  static {
    DubboTelemetry telemetry =
        DubboTelemetry.builder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    new DubboClientNetworkAttributesGetter(), GlobalOpenTelemetry.get()))
            .build();
    clientFilter = telemetry.newClientFilter();
    serverFilter = telemetry.newServerFilter();
    SERVER_INSTRUMENTER =
        emitStableRpcSemconv() ? DubboInternalHelper.getServerInstrumenter(telemetry) : null;
  }

  static Filter clientFilter() {
    return clientFilter;
  }

  static Filter serverFilter() {
    return serverFilter;
  }

  private DubboSingletons() {}
}
