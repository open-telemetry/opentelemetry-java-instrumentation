/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v2_0.AbstractTracedWithSpan;
import io.opentelemetry.instrumentation.rxjava.v2_0.extensionannotation.TracedWithSpan;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class RxJava2ExtensionWithSpanTest extends BaseRxJava2WithSpanTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected AbstractTracedWithSpan newTraced() {
    return new TracedWithSpan();
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
