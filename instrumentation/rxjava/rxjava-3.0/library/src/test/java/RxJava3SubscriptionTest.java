/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.rxjava.v3.common.AbstractRxJava3SubscriptionTest;
import io.opentelemetry.instrumentation.rxjava.v3_0.TracingAssembly;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class RxJava3SubscriptionTest extends AbstractRxJava3SubscriptionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  static TracingAssembly tracingAssembly = TracingAssembly.create();

  @BeforeAll
  public static void setup() {
    tracingAssembly.enable();
  }
}
