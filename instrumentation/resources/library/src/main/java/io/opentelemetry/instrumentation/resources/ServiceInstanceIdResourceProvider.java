/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

/** {@link ResourceProvider} for automatically configuring {@link ServiceInstanceIdResource}. */
@AutoService(ResourceProvider.class)
public final class ServiceInstanceIdResourceProvider implements ConditionalResourceProvider {

  @Override
  public Resource createResource(ConfigProperties config) {
    return ServiceInstanceIdResource.getResource(config);
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    return !config
        .getMap(ServiceInstanceIdResource.RESOURCE_ATTRIBUTES)
        .containsKey(ResourceAttributes.SERVICE_INSTANCE_ID.getKey());
  }
}
