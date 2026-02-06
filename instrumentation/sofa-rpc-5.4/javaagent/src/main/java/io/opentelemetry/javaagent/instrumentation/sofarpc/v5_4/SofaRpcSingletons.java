/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.filter.Filter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.sofarpc.v5_4.SofaRpcTelemetry;
import io.opentelemetry.instrumentation.sofarpc.v5_4.internal.SofaRpcClientNetworkAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class SofaRpcSingletons {
  public static final Filter CLIENT_FILTER;
  public static final Filter SERVER_FILTER;

  static {
    SofaRpcTelemetry telemetry =
        SofaRpcTelemetry.builder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    new SofaRpcClientNetworkAttributesGetter(),
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .build();
    CLIENT_FILTER = telemetry.newClientFilter();
    SERVER_FILTER = telemetry.newServerFilter();
  }

  private SofaRpcSingletons() {}
}
