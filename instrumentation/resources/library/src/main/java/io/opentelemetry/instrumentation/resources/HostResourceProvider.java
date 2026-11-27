/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/** {@link ResourceProvider} for automatically configuring {@link HostResource}. */
@AutoService(ResourceProvider.class)
public final class HostResourceProvider implements ResourceProvider {

  @Override
  public Resource createResource(ConfigProperties config) {
    return HostResource.create(!config.getMap("otel.resource.attributes").containsKey("host.id"));
  }

  @Override
  public int order() {
    // Run before the Java agent's bundled cloud providers so their host attributes take precedence.
    return -1;
  }
}
