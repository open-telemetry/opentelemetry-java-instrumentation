/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1.internal;

import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.model.ConsumerModel;
import org.apache.dubbo.rpc.model.ServiceMetadata;
import org.apache.dubbo.rpc.model.ServiceModel;

/**
 * Dubbo SPI extension selected by {@code DubboTelemetry.instrument(ReferenceConfig)}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class OpenTelemetryClientFilter implements Filter {

  public static final String NAME = "otelManualClient";
  public static final String KEY = OpenTelemetryClientFilter.class.getName();

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    // The proxy's model survives registry refresh and does not depend on RpcContext's consumer URL.
    ServiceModel model = invocation.getServiceModel();
    ServiceMetadata metadata = model instanceof ConsumerModel ? model.getServiceMetadata() : null;
    Object delegate = metadata == null ? null : metadata.getAttribute(KEY);
    return delegate instanceof Filter
        ? ((Filter) delegate).invoke(invoker, invocation)
        : invoker.invoke(invocation);
  }
}
