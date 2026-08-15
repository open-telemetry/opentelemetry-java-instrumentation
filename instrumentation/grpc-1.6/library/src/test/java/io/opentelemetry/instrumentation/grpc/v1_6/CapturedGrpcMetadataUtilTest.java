/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.createExactRequestAttributeKeys;
import static io.opentelemetry.instrumentation.grpc.v1_6.CapturedGrpcMetadataUtil.requestAttributeKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.CapturedNames;
import io.opentelemetry.instrumentation.api.internal.CapturedNames.CaseSensitivity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapturedGrpcMetadataUtilTest {

  @Test
  void retainsOnlyExactRequestAttributeKeys() {
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(asList("literal-key", "wildcard-*", "single-?"))
            .build();

    Map<String, AttributeKey<List<String>>> exactAttributeKeys =
        createExactRequestAttributeKeys(
            CapturedNames.create(selector, CaseSensitivity.CASE_INSENSITIVE));

    assertThat(exactAttributeKeys).containsOnlyKeys("literal-key");
    assertThat(requestAttributeKey("literal-key", exactAttributeKeys))
        .isSameAs(exactAttributeKeys.get("literal-key"));
    assertThat(requestAttributeKey("wildcard-key", exactAttributeKeys))
        .isNotSameAs(requestAttributeKey("wildcard-key", exactAttributeKeys));
  }
}
