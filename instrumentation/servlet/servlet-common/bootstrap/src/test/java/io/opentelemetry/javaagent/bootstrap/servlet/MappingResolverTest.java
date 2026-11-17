/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappingResolverTest {

  @Test
  void preservesMappingOrderAndSnapshot() {
    List<String> mappings = new ArrayList<>(asList("/api/*", "*.json", "/api/data.json"));
    MappingResolver resolver = MappingResolver.build(mappings);
    mappings.clear();

    assertThat(resolver.resolve("/api/data.json", null)).isEqualTo("/api/data.json");
    assertThat(resolver.resolve("/api/other.json", null)).isEqualTo("/api/*");
    assertThat(resolver.resolve("/other.json", null)).isEqualTo("/*.json");
  }
}
