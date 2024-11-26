/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME;
import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

@AutoService(ResourceProvider.class)
public class DistroVersionResourceProvider implements ResourceProvider {

  @Override
  public Resource createResource(ConfigProperties config) {
    return AgentVersion.VERSION == null
        ? Resource.empty()
        : Resource.create(
            Attributes.of(
                TELEMETRY_DISTRO_NAME,
                "opentelemetry-java-instrumentation",
                TELEMETRY_DISTRO_VERSION,
                AgentVersion.VERSION));
  }
}
