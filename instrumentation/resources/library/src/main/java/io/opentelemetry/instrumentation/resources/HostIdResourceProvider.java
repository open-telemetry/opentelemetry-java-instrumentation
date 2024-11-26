/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.instrumentation.resources.HostIdResource.HOST_ID;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/**
 * {@link ResourceProvider} for automatically configuring <code>host.id</code> according to <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/resource/host.md#non-privileged-machine-id-lookup">the
 * semantic conventions</a>
 */
public final class HostIdResourceProvider implements ConditionalResourceProvider {

  @Override
  public Resource createResource(ConfigProperties config) {
    return HostResource.get();
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    return !config.getMap("otel.resource.attributes").containsKey(HOST_ID.getKey())
        && existing.getAttribute(HOST_ID) == null;
  }

  @Override
  public int order() {
    // Run after cloud provider resource providers
    return Integer.MAX_VALUE - 1;
  }
}
