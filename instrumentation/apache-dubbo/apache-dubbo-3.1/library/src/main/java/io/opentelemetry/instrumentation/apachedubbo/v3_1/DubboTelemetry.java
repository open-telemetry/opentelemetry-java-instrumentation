/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.DubboInstrumenters;
import io.opentelemetry.instrumentation.apachedubbo.v3_1.internal.OpenTelemetryClientFilter;
import io.opentelemetry.instrumentation.apachedubbo.v3_1.internal.OpenTelemetryServerFilter;
import javax.annotation.Nullable;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.Filter;

/** Entry point for instrumenting Apache Dubbo consumers and providers. */
public final class DubboTelemetry {

  private final DubboInstrumenters instrumenters;

  /** Returns a new {@link DubboTelemetry} configured with the given {@link OpenTelemetry}. */
  public static DubboTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry}. */
  public static DubboTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new DubboTelemetryBuilder(openTelemetry);
  }

  DubboTelemetry(DubboInstrumenters instrumenters) {
    this.instrumenters = instrumenters;
  }

  /**
   * Enables tracing for this reference. Call before initializing the reference. Repeated calls
   * replace the binding without adding another filter.
   */
  public void instrument(ReferenceConfig<?> referenceConfig) {
    requireNonNull(referenceConfig, "referenceConfig");
    referenceConfig.setFilter(
        appendFilter(referenceConfig.getFilter(), OpenTelemetryClientFilter.NAME));
    referenceConfig
        .getServiceMetadata()
        .addAttribute(OpenTelemetryClientFilter.KEY, newClientFilter());
  }

  /**
   * Enables tracing for this service. Call before exporting the service. Repeated calls replace the
   * binding without adding another filter.
   */
  public void instrument(ServiceConfig<?> serviceConfig) {
    requireNonNull(serviceConfig, "serviceConfig");
    serviceConfig.setFilter(
        appendFilter(serviceConfig.getFilter(), OpenTelemetryServerFilter.NAME));
    serviceConfig
        .getServiceMetadata()
        .addAttribute(OpenTelemetryServerFilter.KEY, newServerFilter());
  }

  /** Returns a new Dubbo client {@link Filter} that traces RPC invocations. */
  public Filter newClientFilter() {
    return instrumenters.newClientFilter();
  }

  /** Returns a new Dubbo server {@link Filter} that traces RPC invocations. */
  public Filter newServerFilter() {
    return instrumenters.newServerFilter();
  }

  private static String appendFilter(@Nullable String filters, String name) {
    if (filters == null || filters.trim().isEmpty()) {
      return name;
    }
    for (String filter : filters.split(",")) {
      if (filter.trim().equals(name)) {
        return filters;
      }
    }
    return filters + "," + name;
  }
}
