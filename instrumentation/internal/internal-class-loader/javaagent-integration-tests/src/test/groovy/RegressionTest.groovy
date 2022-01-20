/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class RegressionTest extends AgentInstrumentationSpecification {

  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/5155
  // loading a class that is extended/implemented by a helper class causes
  // java.lang.LinkageError: loader 'app' (instance of jdk.internal.loader.ClassLoaders$AppClassLoader) attempted duplicate interface definition for org.apache.commons.lang3.function.FailableCallable
  // this test verifies that the duplicate class definition LinkageError is not thrown into
  // application code
  def "test no duplicate class definition"() {
    expect:
    Class.forName("org.apache.commons.lang3.function.FailableCallable") != null
  }
}
