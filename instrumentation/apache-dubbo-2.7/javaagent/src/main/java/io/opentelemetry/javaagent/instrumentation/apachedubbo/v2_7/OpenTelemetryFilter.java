/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboTracing;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboNetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;

@Activate(group = {"consumer", "provider"})
public class OpenTelemetryFilter implements Filter {

  private final Filter delegate;

  public OpenTelemetryFilter() {
    DubboNetServerAttributesExtractor netAttributesExtractor =
        new DubboNetServerAttributesExtractor();
    delegate =
        DubboTracing.newBuilder(GlobalOpenTelemetry.get())
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .build()
            .newFilter();
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    return delegate.invoke(invoker, invocation);
  }
}
