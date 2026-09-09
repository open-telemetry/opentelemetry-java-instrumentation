/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenSearchAttributesGetterTest {

  private final OpenSearchAttributesGetter getter = new OpenSearchAttributesGetter();

  @Test
  void returnsCapturedQueryBody() {
    OpenSearchRequest request = OpenSearchRequest.create("POST", "/_search", "query body", null);

    assertThat(getter.getDbQueryText(request)).isEqualTo("query body");
  }

  @Test
  void returnsLegacyStatementWithoutCapturedBody() {
    OpenSearchRequest request = OpenSearchRequest.create("POST", "/_search", null, null);

    assertThat(getter.getDbQueryText(request)).isEqualTo("POST /_search");
  }
}
