/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ServiceInstanceIdResourceTest {

  private static class Parameter {
    public final Map<String, String> resource;
    public final String want;

    public Parameter(Map<String, String> resource, String want) {
      this.resource = resource;
      this.want = want;
    }
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource
  void serviceInstanceId(Parameter parameter) {
    Resource r =
        ServiceInstanceIdResource.getResource(
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    ServiceInstanceIdResource.RESOURCE_ATTRIBUTES,
                    parameter.resource.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(",")))));
    assertThat(r.getAttribute(ResourceAttributes.SERVICE_INSTANCE_ID)).matches(parameter.want);
  }

  public static Stream<Arguments> serviceInstanceId() {
    return Stream.of(
        Arguments.of(
            named(
                "service instance id is not set, but pod name and container name are",
                new Parameter(
                    ImmutableMap.of(
                        ResourceAttributes.K8S_POD_NAME.getKey(),
                        "pod-12345",
                        ResourceAttributes.K8S_CONTAINER_NAME.getKey(),
                        "container-42"),
                    "pod-12345/container-42"))),
        Arguments.of(
            named(
                "fall back to random service instance id",
                new Parameter(
                    ImmutableMap.of(ResourceAttributes.K8S_POD_NAME.getKey(), "pod-12345"),
                    "........-....-....-....-............"))));
  }
}
