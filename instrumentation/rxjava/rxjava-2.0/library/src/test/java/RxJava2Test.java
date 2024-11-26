/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v2_0.AbstractRxJava2Test;
import io.opentelemetry.instrumentation.rxjava.v2_0.TracingAssembly;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RxJava2Test extends AbstractRxJava2Test {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static TracingAssembly tracingAssembly = TracingAssembly.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  public void setup() {
    tracingAssembly.enable();
  }
}
