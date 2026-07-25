/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/** {@link ResourceProvider} for automatically configuring {@link ProcessResource}. */
@AutoService(ResourceProvider.class)
public final class ProcessResourceProvider implements ResourceProvider {

  private static final String V3_PREVIEW_CONFIG = "otel.instrumentation.common.v3-preview";
  private static final String CAPTURE_COMMAND_ATTRIBUTES_CONFIG =
      "otel.instrumentation.resources.experimental.process-command-attributes.enabled";

  @Override
  public Resource createResource(ConfigProperties config) {
    return ProcessResource.create(emitCommandAttributes(config));
  }

  private static boolean emitCommandAttributes(ConfigProperties config) {
    return !config.getBoolean(V3_PREVIEW_CONFIG, false)
        || config.getBoolean(CAPTURE_COMMAND_ATTRIBUTES_CONFIG, false);
  }
}
