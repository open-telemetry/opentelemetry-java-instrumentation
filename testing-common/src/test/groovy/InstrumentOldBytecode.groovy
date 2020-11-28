/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.ibm.as400.resource.ResourceLevel
import io.opentelemetry.instrumentation.test.AgentTestRunner
import spock.lang.Ignore

// FIXME (trask)
@Ignore
class InstrumentOldBytecode extends AgentTestRunner {
  def "can instrument old bytecode"() {
    expect:
    new ResourceLevel().toString() == "instrumented"
  }
}
