/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServiceMappingResolver;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 *
 * <p>Compatibility entry point for service mapping resolution.
 *
 * @deprecated Use {@link ServiceMappingResolver} instead. May be removed in the next minor release.
 */
@Deprecated // may be removed in the next minor release
public class ServicePeerResolver extends ServiceMappingResolver {

  /**
   * Creates a service mapping resolver.
   *
   * @deprecated Use {@link ServiceMappingResolver#ServiceMappingResolver(OpenTelemetry)} instead.
   *     May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public ServicePeerResolver(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }
}
