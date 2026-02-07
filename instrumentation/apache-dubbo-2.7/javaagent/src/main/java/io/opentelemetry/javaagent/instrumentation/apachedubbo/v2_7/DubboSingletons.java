/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import org.apache.dubbo.rpc.Filter;

public final class DubboSingletons {
  public static final Filter CLIENT_FILTER;
  public static final Filter SERVER_FILTER;

  static {
    DubboTelemetry telemetry =
        DubboTelemetry.builder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    new DubboClientNetworkAttributesGetter(), GlobalOpenTelemetry.get()))
            .build();
    CLIENT_FILTER = telemetry.newClientFilter();
    SERVER_FILTER = telemetry.newServerFilter();
  }

  private DubboSingletons() {}
}
