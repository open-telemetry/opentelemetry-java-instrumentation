/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
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
  @Nullable private static final Instrumenter<DubboRequest, Result> serverInstrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    DubboTelemetry telemetry =
        DubboTelemetry.builder(openTelemetry)
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    new DubboClientNetworkAttributesGetter(openTelemetry), openTelemetry))
            .build();
    clientFilter = telemetry.newClientFilter();
    serverFilter = telemetry.newServerFilter();
    serverInstrumenter =
        emitPreviewRpcSemconv(openTelemetry)
            ? DubboInternalHelper.getServerInstrumenter(telemetry)
            : null;
  }

  static Filter clientFilter() {
    return clientFilter;
  }

  static Filter serverFilter() {
    return serverFilter;
  }

  @Nullable
  static Instrumenter<DubboRequest, Result> serverInstrumenter() {
    return serverInstrumenter;
  }

  private DubboSingletons() {}
}
