/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.DubboInstrumenterBuilder;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.DubboInstrumenters;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.ServerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Result;

class DubboSingletons {
  private static final Filter clientFilter;
  private static final Filter serverFilter;
  private static final Instrumenter<DubboRequest, Result> serverInstrumenter;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    DubboInstrumenters telemetry =
        new DubboInstrumenterBuilder(openTelemetry, "io.opentelemetry.apache-dubbo-2.7")
            .addClientAttributeExtractor(
                ServerServiceAttributesExtractor.create(
                    new DubboClientNetworkAttributesGetter(), openTelemetry))
            .build();
    clientFilter = telemetry.newClientFilter();
    serverFilter = telemetry.newServerFilter();
    serverInstrumenter = telemetry.serverInstrumenter();
  }

  static Filter clientFilter() {
    return clientFilter;
  }

  static Filter serverFilter() {
    return serverFilter;
  }

  static Instrumenter<DubboRequest, Result> serverInstrumenter() {
    return serverInstrumenter;
  }

  private DubboSingletons() {}
}
