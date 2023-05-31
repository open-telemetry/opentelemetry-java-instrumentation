/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/** {@link ResourceProvider} for automatically configuring {@link EnvironmentResource}. */
@AutoService(ResourceProvider.class)
public final class EnvironmentResourceProvider implements ResourceProvider {
  @Override
  public Resource createResource(ConfigProperties config) {
    return EnvironmentResource.create(config);
  }

  @Override
  public int order() {
    // A high order ensures that the environment resource takes precedent over the resources from
    // other ResourceProviders
    return 10;
  }
}
