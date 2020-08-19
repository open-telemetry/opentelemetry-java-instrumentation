/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.test

import com.google.common.base.Predicate
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.trace.data.SpanData
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
      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec)

  abstract void assertTracesWithFilter(
    final int size,
    final Predicate<List<SpanData>> excludes,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec)
}
