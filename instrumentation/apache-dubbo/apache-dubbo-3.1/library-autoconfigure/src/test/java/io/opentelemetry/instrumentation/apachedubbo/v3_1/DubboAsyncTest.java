/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.AbstractDubboAsyncTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class DubboAsyncTest extends AbstractDubboAsyncTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected String instrumentationScopeName() {
    return "io.opentelemetry.apache-dubbo-3.1";
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
