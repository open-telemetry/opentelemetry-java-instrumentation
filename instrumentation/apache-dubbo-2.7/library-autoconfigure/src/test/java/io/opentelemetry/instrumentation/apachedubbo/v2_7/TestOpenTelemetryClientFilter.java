/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
import java.util.HashMap;
import java.util.Map;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;

@Activate(
    group = {"consumer"},
    order = -1)
public final class TestOpenTelemetryClientFilter implements Filter {

  private final Filter delegate;

  public TestOpenTelemetryClientFilter() {
    // Create peer service mapping for testing
    Map<String, String> peerServiceMapping = new HashMap<>();
    peerServiceMapping.put("127.0.0.1", "test-peer-service");
    peerServiceMapping.put("localhost", "test-peer-service");
    peerServiceMapping.put("192.0.2.1", "test-peer-service");

    delegate =
        DubboTelemetry.builder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(
                    new DubboClientNetworkAttributesGetter(),
                    PeerServiceResolver.create(peerServiceMapping)))
            .build()
            .newClientFilter();
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    return delegate.invoke(invoker, invocation);
  }
}
