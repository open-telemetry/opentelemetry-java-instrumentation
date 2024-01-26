/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

public class DistroVersionResourceProvider implements ResourceProvider {

  public static final String VERSION =
      EmbeddedInstrumentationProperties.findVersion("io.opentelemetry.spring-boot-autoconfigure");

  private static final AttributeKey<String> TELEMETRY_DISTRO_NAME =
      AttributeKey.stringKey("telemetry.distro.name");
  private static final AttributeKey<String> TELEMETRY_DISTRO_VERSION =
      AttributeKey.stringKey("telemetry.distro.version");

  @Override
  public Resource createResource(ConfigProperties config) {
    return Resource.create(
        Attributes.of(
            TELEMETRY_DISTRO_NAME,
            "opentelemetry-spring-boot-starter",
            TELEMETRY_DISTRO_VERSION,
            VERSION));
  }
}
