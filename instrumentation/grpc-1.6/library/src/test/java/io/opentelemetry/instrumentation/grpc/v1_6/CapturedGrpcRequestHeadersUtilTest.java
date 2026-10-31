/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcRequestHeadersUtil.createLiteralRequestHeaderAttributeKeys;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcRequestHeadersUtil.requestHeaderAttributeKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapturedGrpcRequestHeadersUtilTest {

  @Test
  void retainsOnlyLiteralRequestHeaderAttributeKeys() {
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(asList("literal-key", "wildcard-*", "single-?"))
            .build();

    Map<String, AttributeKey<List<String>>> literalRequestHeaderAttributeKeys =
        createLiteralRequestHeaderAttributeKeys(selector);

    assertThat(literalRequestHeaderAttributeKeys).containsOnlyKeys("literal-key");
    assertThat(requestHeaderAttributeKey("literal-key", literalRequestHeaderAttributeKeys))
        .isSameAs(literalRequestHeaderAttributeKeys.get("literal-key"));
    assertThat(requestHeaderAttributeKey("wildcard-key", literalRequestHeaderAttributeKeys))
        .isNotSameAs(requestHeaderAttributeKey("wildcard-key", literalRequestHeaderAttributeKeys));
    assertThat(requestHeaderAttributeKey("literal-key", literalRequestHeaderAttributeKeys).getKey())
        .isEqualTo("rpc.request.header.literal-key");
  }
}
