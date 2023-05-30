/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentResourceTest {

  @Test
  void customConfigResource() {
    Map<String, String> props = new HashMap<>();
    props.put("otel.service.name", "test-service");
    props.put("otel.resource.attributes", "food=cheesecake,drink=juice");

    assertThat(EnvironmentResource.create(DefaultConfigProperties.create(props)))
        .isEqualTo(
            Resource.empty().toBuilder()
                .put(ResourceAttributes.SERVICE_NAME, "test-service")
                .put("food", "cheesecake")
                .put("drink", "juice")
                .setSchemaUrl(ResourceAttributes.SCHEMA_URL)
                .build());
  }

  @Test
  void resourceFromConfig_empty() {
    Attributes attributes =
        EnvironmentResource.getAttributes(DefaultConfigProperties.createForTest(emptyMap()));

    assertThat(attributes).isEmpty();
  }

  @Test
  void resourceFromConfig() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                singletonMap(
                    EnvironmentResource.ATTRIBUTE_PROPERTY,
                    "service.name=myService,appName=MyApp")));

    assertThat(attributes)
        .hasSize(2)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "myService")
        .containsEntry("appName", "MyApp");
  }

  @Test
  void serviceName() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                singletonMap(EnvironmentResource.SERVICE_NAME_PROPERTY, "myService")));

    assertThat(attributes).hasSize(1).containsEntry(ResourceAttributes.SERVICE_NAME, "myService");
  }

  @Test
  void resourceFromConfig_overrideServiceName() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    EnvironmentResource.ATTRIBUTE_PROPERTY,
                    "service.name=myService,appName=MyApp",
                    EnvironmentResource.SERVICE_NAME_PROPERTY,
                    "ReallyMyService")));

    assertThat(attributes)
        .hasSize(2)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "ReallyMyService")
        .containsEntry("appName", "MyApp");
  }

  @Test
  void resourceFromConfig_emptyEnvVar() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                singletonMap(EnvironmentResource.ATTRIBUTE_PROPERTY, "")));

    assertThat(attributes).isEmpty();
  }
}
