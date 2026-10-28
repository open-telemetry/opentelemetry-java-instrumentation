/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static io.opentelemetry.instrumentation.testing.util.ResourceSchemaAssertions.assertResourceEntitySchemasMatchSdk;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.internal.Entity;
import io.opentelemetry.sdk.resources.internal.EntityUtil;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServiceResourceTest {

  @ParameterizedTest
  @MethodSource("resources")
  void create(String name, String version, Resource expectedResource) {
    Resource resource = ServiceResource.create(name, version);
    assertThat(resource).isEqualTo(expectedResource);
    assertResourceEntitySchemasMatchSdk(resource);
  }

  private static Stream<Arguments> resources() {
    return Stream.of(
        argumentSet("empty", null, null, Resource.empty()),
        argumentSet(
            "name",
            "my-service",
            null,
            EntityUtil.createResource(
                singletonList(
                    Entity.builder("service", Attributes.of(SERVICE_NAME, "my-service"))
                        .setSchemaUrl(SchemaUrls.V1_40_0)
                        .build()))),
        argumentSet(
            "name and version",
            "my-service",
            "1.0",
            EntityUtil.createResource(
                singletonList(
                    Entity.builder("service", Attributes.of(SERVICE_NAME, "my-service"))
                        .setSchemaUrl(SchemaUrls.V1_40_0)
                        .setDescription(Attributes.of(SERVICE_VERSION, "1.0"))
                        .build()))),
        argumentSet(
            "missing identity",
            null,
            "1.0",
            Resource.create(Attributes.of(SERVICE_VERSION, "1.0"))));
  }
}
