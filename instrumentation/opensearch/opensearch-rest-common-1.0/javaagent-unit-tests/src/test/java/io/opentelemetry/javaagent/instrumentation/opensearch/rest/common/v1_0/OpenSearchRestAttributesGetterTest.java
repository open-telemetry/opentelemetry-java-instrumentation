/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenSearchRestAttributesGetterTest {

  @Test
  void returnsLegacyStatement() {
    OpenSearchRestRequest request = OpenSearchRestRequest.create("POST", "/_search", null);

    assertThat(new OpenSearchRestAttributesGetter().getDbQueryText(request))
        .isEqualTo("POST /_search");
  }
}
