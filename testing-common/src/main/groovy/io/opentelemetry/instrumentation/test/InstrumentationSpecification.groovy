/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test


import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.instrumentation.test.asserts.InMemoryExporterAssert
import spock.lang.Specification

/**
 * Base class for test specifications that are shared between instrumentation libraries and agent.
 * The methods in this class are implemented by {@link AgentTestTrait} and
 * {@link InstrumentationTestTrait}.
 */
abstract class InstrumentationSpecification extends Specification {
  abstract void assertTraces(
    final int size,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec)
}
