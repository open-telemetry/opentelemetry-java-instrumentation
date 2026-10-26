/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.event.Level;

class ResourceProviderRegistrationTest {

  @RegisterExtension
  LogCapturer logs = LogCapturer.create().captureForType(Resource.class, Level.INFO);

  @ParameterizedTest
  @CsvSource({
    "otel.service.name, configured-service",
    "otel.resource.attributes, service.name=configured-service"
  })
  void resourceProvidersMergeWithConfiguredService(String property, String value) {
    Map<String, String> properties = new HashMap<>();
    properties.put(property, value);
    properties.put("otel.traces.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.logs.exporter", "none");
    AtomicReference<Resource> configuredResource = new AtomicReference<>();

    try (OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> properties)
            .addResourceCustomizer(
                (resource, config) -> {
                  configuredResource.set(resource);
                  return resource;
                })
            .build()
            .getOpenTelemetrySdk()) {
      Resource resource = configuredResource.get();
      assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo("configured-service");
      assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_40_0);
      assertThat(resource.getAttributes().asMap())
          .containsAllEntriesOf(HostResource.get().getAttributes().asMap())
          .containsAllEntriesOf(OsResource.get().getAttributes().asMap())
          .containsAllEntriesOf(ProcessResource.create().getAttributes().asMap())
          .containsAllEntriesOf(ProcessRuntimeResource.get().getAttributes().asMap());
    }

    assertThat(logs.getEvents()).isEmpty();
  }

  @Test
  void resourceProvidersAreRegistered() {
    assertThat(ServiceLoader.load(ResourceProvider.class))
        .filteredOn(
            provider ->
                provider
                    .getClass()
                    .getPackage()
                    .getName()
                    .equals("io.opentelemetry.instrumentation.resources"))
        .extracting(provider -> provider.getClass().getSimpleName())
        .containsExactlyInAnyOrder(
            "ContainerResourceProvider",
            "HostIdResourceProvider",
            "HostResourceProvider",
            "JarServiceNameDetector",
            "ManifestResourceProvider",
            "OsResourceProvider",
            "ProcessResourceProvider",
            "ProcessRuntimeResourceProvider");
  }
}
