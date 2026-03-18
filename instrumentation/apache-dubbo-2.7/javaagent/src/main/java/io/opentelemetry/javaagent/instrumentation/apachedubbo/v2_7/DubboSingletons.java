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

public final class DubboSingletons {
  public static final Filter CLIENT_FILTER;
  public static final Filter SERVER_FILTER;

  // Non-null only when stable RPC semconv is opted in; used by unknown-service instrumentation.
  // Public because ByteBuddy advice accesses this field across classloader boundaries.
  @Nullable public static final Instrumenter<DubboRequest, Result> SERVER_INSTRUMENTER;

  static {
    DubboTelemetry telemetry =
        DubboTelemetry.builder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    new DubboClientNetworkAttributesGetter(), GlobalOpenTelemetry.get()))
            .build();
    CLIENT_FILTER = telemetry.newClientFilter();
    SERVER_FILTER = telemetry.newServerFilter();
    SERVER_INSTRUMENTER =
        emitStableRpcSemconv() ? DubboInternalHelper.getServerInstrumenter(telemetry) : null;
  }

  private DubboSingletons() {}
}
