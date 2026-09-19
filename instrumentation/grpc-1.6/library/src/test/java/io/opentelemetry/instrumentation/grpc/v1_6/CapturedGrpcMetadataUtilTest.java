/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.createLiteralRequestAttributeKeys;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.requestAttributeKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapturedGrpcMetadataUtilTest {

  @Test
  void retainsOnlyLiteralRequestAttributeKeys() {
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(asList("literal-key", "wildcard-*", "single-?"))
            .build();

    Map<String, AttributeKey<List<String>>> literalAttributeKeys =
        createLiteralRequestAttributeKeys(selector);

    assertThat(literalAttributeKeys).containsOnlyKeys("literal-key");
    assertThat(requestAttributeKey("literal-key", literalAttributeKeys))
        .isSameAs(literalAttributeKeys.get("literal-key"));
    assertThat(requestAttributeKey("wildcard-key", literalAttributeKeys))
        .isNotSameAs(requestAttributeKey("wildcard-key", literalAttributeKeys));
  }
}
