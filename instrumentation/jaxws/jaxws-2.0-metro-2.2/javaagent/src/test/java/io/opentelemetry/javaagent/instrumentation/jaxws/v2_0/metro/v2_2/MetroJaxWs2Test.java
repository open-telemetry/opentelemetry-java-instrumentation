/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.metro.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.AbstractJaxWs2Test;
import org.junit.jupiter.api.AfterEach;

class MetroJaxWs2Test extends AbstractJaxWs2Test {

  @AfterEach
  void assertServiceScope() {
    assertThat(testing().getExportedSpans())
        .filteredOn(span -> span.getName().startsWith("HelloService/"))
        .isNotEmpty()
        .extracting(span -> span.getInstrumentationScopeInfo().getName())
        .containsOnly("io.opentelemetry.jaxws-2.0-metro-2.2");
  }
}
