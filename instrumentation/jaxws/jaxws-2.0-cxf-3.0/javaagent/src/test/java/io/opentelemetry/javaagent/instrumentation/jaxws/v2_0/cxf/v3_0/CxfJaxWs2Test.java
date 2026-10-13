/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.cxf.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.AbstractJaxWs2Test;
import org.junit.jupiter.api.AfterEach;

class CxfJaxWs2Test extends AbstractJaxWs2Test {

  @AfterEach
  void assertServiceScope() {
    assertThat(testing().getExportedSpans())
        .filteredOn(span -> span.getName().startsWith("HelloService/"))
        .isNotEmpty()
        .extracting(span -> span.getInstrumentationScopeInfo().getName())
        .containsOnly("io.opentelemetry.jaxws-2.0-cxf-3.0");
  }
}
