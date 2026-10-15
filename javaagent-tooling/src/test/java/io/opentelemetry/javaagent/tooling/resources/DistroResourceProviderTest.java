/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.resources;

import static io.opentelemetry.semconv.TelemetryAttributes.TELEMETRY_DISTRO_NAME;
import static io.opentelemetry.semconv.TelemetryAttributes.TELEMETRY_DISTRO_VERSION;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.javaagent.tooling.AgentVersion;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DistroResourceProviderTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void flatConfig() {
    Resource resource =
        new DistroResourceProvider()
            .createResource(DefaultConfigProperties.createFromMap(emptyMap()));

    assertThat(resource.getAttributes())
        .isEqualTo(
            Attributes.of(
                TELEMETRY_DISTRO_NAME,
                "opentelemetry-javaagent",
                TELEMETRY_DISTRO_VERSION,
                AgentVersion.VERSION));
  }

  @Test
  void declarativeConfig() {
    Resource resource =
        new DistroComponentProvider().create(mock(DeclarativeConfigProperties.class));

    assertThat(resource.getAttributes())
        .isEqualTo(
            Attributes.of(
                TELEMETRY_DISTRO_NAME,
                "opentelemetry-javaagent",
                TELEMETRY_DISTRO_VERSION,
                AgentVersion.VERSION));
  }

  @Test
  void resourceAttributesOverrideDistro() {
    AutoConfiguredOpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(
                () ->
                    singletonMap(
                        "otel.resource.attributes",
                        "telemetry.distro.name=custom-distro,telemetry.distro.version=1.2.3"))
            .build();
    cleanup.deferCleanup(sdk.getOpenTelemetrySdk());

    assertThat(SdkAutoconfigureAccess.getResource(sdk).getAttributes().asMap())
        .containsEntry(TELEMETRY_DISTRO_NAME, "custom-distro")
        .containsEntry(TELEMETRY_DISTRO_VERSION, "1.2.3");
  }

  @Test
  void resourceCustomizerOverridesDistro() {
    AutoConfiguredOpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addResourceCustomizer(
                (resource, config) ->
                    resource.merge(
                        Resource.create(
                            Attributes.of(
                                TELEMETRY_DISTRO_NAME,
                                "custom-distro",
                                TELEMETRY_DISTRO_VERSION,
                                "1.2.3"))))
            .build();
    cleanup.deferCleanup(sdk.getOpenTelemetrySdk());

    assertThat(SdkAutoconfigureAccess.getResource(sdk).getAttributes().asMap())
        .containsEntry(TELEMETRY_DISTRO_NAME, "custom-distro")
        .containsEntry(TELEMETRY_DISTRO_VERSION, "1.2.3");
  }
}
