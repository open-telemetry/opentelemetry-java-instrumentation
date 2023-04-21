/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v3.common.AbstractRxJava3WithSpanTest;
import io.opentelemetry.instrumentation.rxjava.v3.common.AbstractTracedWithSpan;
import io.opentelemetry.instrumentation.rxjava.v3.common.extensionannotation.TracedWithSpan;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RxJava3InstrumentationWithSpanTest extends AbstractRxJava3WithSpanTest {
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
