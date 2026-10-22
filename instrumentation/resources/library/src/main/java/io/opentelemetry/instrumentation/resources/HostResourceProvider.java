/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/** {@link ResourceProvider} for automatically configuring {@link HostResource}. */
@AutoService(ResourceProvider.class)
public final class HostResourceProvider implements ConditionalResourceProvider {

  // copied from HostIncubatingAttributes
  private static final AttributeKey<String> HOST_ID = AttributeKey.stringKey("host.id");

  private boolean hasHostId;

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    hasHostId = existing.getAttribute(HOST_ID) != null;
    return true;
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    return HostResource.create(
        config.getBoolean("otel.resource.providers.host-id.enabled", false)
            && !config.getMap("otel.resource.attributes").containsKey(HOST_ID.getKey())
            && !hasHostId);
  }

  @Override
  public int order() {
    // Run after cloud resource providers so their host ID takes precedence.
    return Integer.MAX_VALUE - 1;
  }
}
