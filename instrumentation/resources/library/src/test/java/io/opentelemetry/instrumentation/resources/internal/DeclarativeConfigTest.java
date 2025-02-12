/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.extension.incubator.fileconfig.FileConfiguration;
import io.opentelemetry.sdk.resources.Resource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class DeclarativeConfigTest {

  @Test
  void endToEnd() {
    String yaml =
        "file_format: 0.3\n"
            + "resource:\n"
            + "  attributes:\n"
            + "    - name: service.name\n"
            + "      value: my-service\n"
            + "tracer_provider:\n";

    boolean java8 = "1.8".equals(System.getProperty("java.specification.version"));
    OpenTelemetrySdk openTelemetrySdk =
        FileConfiguration.parseAndCreate(
            new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    assertThat(openTelemetrySdk.getSdkTracerProvider())
        .extracting("sharedState.resource", as(InstanceOfAssertFactories.type(Resource.class)))
        .satisfies(
            resource -> {
              // From .resource.attributes
              assertThat(resource.getAttribute(AttributeKey.stringKey("service.name")))
                  .isEqualTo("my-service");

              // From ComponentProvider SPI
              Set<String> attributeKeys =
                  resource.getAttributes().asMap().keySet().stream()
                      .map(AttributeKey::getKey)
                      .collect(Collectors.toSet());
              // ContainerResourceComponentProvider - no container attributes reliably provided
              // HostIdResourceComponentProvider - host.id attribute not reliably provided
              // HostResourceComponentProvider
              assertThat(attributeKeys).contains("host.arch");
              assertThat(attributeKeys).contains("host.name");
              // OsResourceComponentProvider
              assertThat(attributeKeys).contains("os.description");
              assertThat(attributeKeys).contains("os.type");
              // ProcessResourceComponentProvider
              assertThat(attributeKeys)
                  .contains(java8 ? "process.command_line" : "process.command_args");
              assertThat(attributeKeys).contains("process.executable.path");
              assertThat(attributeKeys).contains("process.pid");
              // ProcessRuntimeResourceComponentProvider
              assertThat(attributeKeys).contains("process.runtime.description");
              assertThat(attributeKeys).contains("process.runtime.name");
              assertThat(attributeKeys).contains("process.runtime.version");
            });
  }
}
