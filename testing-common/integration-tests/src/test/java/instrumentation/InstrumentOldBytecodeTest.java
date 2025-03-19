/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstrumentOldBytecodeTest {

  @Test
  @SuppressWarnings("deprecation") // com.ibm.as400.resource.ResourceLevel is deprecated
  void canInstrumentOldBytecode() {
    assertThat(new com.ibm.as400.resource.ResourceLevel().toString()).isEqualTo("instrumented");
  }
}
