/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.ibm.as400.resource.ResourceLevel
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class InstrumentOldBytecode extends AgentInstrumentationSpecification {
  def "can instrument old bytecode"() {
    expect:
    new ResourceLevel().toString() == "instrumented"
  }
}
