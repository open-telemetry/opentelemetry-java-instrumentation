/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenSearchEndpointSanitizerTest {

  @ParameterizedTest
  @MethodSource("endpoints")
  void sanitizeByKeyword(String endpoint, String expected) {
    assertThat(OpenSearchEndpointSanitizer.sanitizeByKeyword(endpoint)).isEqualTo(expected);
  }

  private static Stream<Arguments> endpoints() {
    return Stream.of(
        // every keyword that introduces a single document id
        arguments("test-index/_doc/12345", "test-index/_doc/?"),
        arguments("test-index/_create/12345", "test-index/_create/?"),
        arguments("test-index/_update/12345", "test-index/_update/?"),
        arguments("test-index/_source/12345", "test-index/_source/?"),
        arguments("test-index/_explain/12345", "test-index/_explain/?"),
        arguments("test-index/_termvectors/12345", "test-index/_termvectors/?"),
        // leading and repeated separators keep their position
        arguments("/test-index/_doc/12345", "/test-index/_doc/?"),
        arguments("test-index/_doc//12345", "test-index/_doc//?"),
        // only the segment right after the keyword is masked
        arguments("test-index/_doc/12345/_source", "test-index/_doc/?/_source"),
        arguments("test-index/_doc/_source/_source", "test-index/_doc/?/_source"),
        // endpoints that carry no document id keep their structure
        arguments("_cluster/health", "_cluster/health"),
        arguments("test-index/_search", "test-index/_search"),
        arguments("test-index/_update_by_query", "test-index/_update_by_query"),
        arguments("test-index/_doc", "test-index/_doc"),
        arguments("test-index/_doc/", "test-index/_doc/"),
        // the legacy typed document route carries no keyword, so the fallback leaves it intact.
        // The route table masks its id instead, which OpenSearchEndpointMapTest pins.
        arguments("test-index/test-type/12345", "test-index/test-type/12345"),
        arguments("", ""));
  }
}
