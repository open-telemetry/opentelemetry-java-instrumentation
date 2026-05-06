/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.filter.Filter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.sofarpc.v5_4.SofaRpcTelemetry;
import io.opentelemetry.instrumentation.sofarpc.v5_4.internal.SofaRpcClientNetworkAttributesGetter;

public final class SofaRpcSingletons {
  private static final Filter clientFilter;
  private static final Filter serverFilter;

  static {
    SofaRpcTelemetry telemetry =
        SofaRpcTelemetry.builder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    new SofaRpcClientNetworkAttributesGetter(), GlobalOpenTelemetry.get()))
            .build();
    clientFilter = telemetry.newClientFilter();
    serverFilter = telemetry.newServerFilter();
  }

  public static Filter clientFilter() {
    return clientFilter;
  }

  public static Filter serverFilter() {
    return serverFilter;
  }

  private SofaRpcSingletons() {}
}
