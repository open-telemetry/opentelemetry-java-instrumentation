/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1.internal;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.apache.dubbo.rpc.model.ServiceMetadata;
import org.apache.dubbo.rpc.model.ServiceModel;

/**
 * Dubbo SPI extension selected by {@code DubboTelemetry.instrument(ServiceConfig)}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// No activation group: order explicit selections without activating on consumers or providers.
@Activate(order = Integer.MIN_VALUE)
public final class OpenTelemetryServerFilter implements Filter {

  public static final String NAME = "otelManualServer";
  public static final String KEY = OpenTelemetryServerFilter.class.getName();

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) {
    ServiceModel model = invoker.getUrl().getServiceModel();
    ServiceMetadata metadata = model instanceof ProviderModel ? model.getServiceMetadata() : null;
    Object delegate = metadata == null ? null : metadata.getAttribute(KEY);
    return delegate instanceof Filter
        ? ((Filter) delegate).invoke(invoker, invocation)
        : invoker.invoke(invocation);
  }
}
