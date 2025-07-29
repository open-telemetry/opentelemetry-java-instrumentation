/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import org.apache.dubbo.rpc.Filter;

public final class DubboSingletons {
  public static final Filter CLIENT_FILTER;
  public static final Filter SERVER_FILTER;

  static {
    DubboTelemetry telemetry =
        DubboTelemetry.builder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    new DubboClientNetworkAttributesGetter(),
                    AgentCommonConfig.get().getPeerServiceResolver()))
            .build();
    CLIENT_FILTER = telemetry.newClientFilter();
    SERVER_FILTER = telemetry.newServerFilter();
  }

  private DubboSingletons() {}
}
