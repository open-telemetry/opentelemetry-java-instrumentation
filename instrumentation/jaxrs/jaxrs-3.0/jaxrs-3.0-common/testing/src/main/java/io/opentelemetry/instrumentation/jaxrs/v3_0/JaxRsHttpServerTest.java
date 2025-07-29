/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0;

import io.opentelemetry.instrumentation.jaxrs.AbstractJaxRsHttpServerTest;
import io.opentelemetry.instrumentation.jaxrs.v3_0.test.JaxRsTestResource;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;

public abstract class JaxRsHttpServerTest<SERVER> extends AbstractJaxRsHttpServerTest<SERVER> {

  @BeforeEach
  void setup() {
    // reset the barrier to avoid a failing test breaking subsequent tests
    JaxRsTestResource.BARRIER.reset();
  }

  @Override
  protected void awaitBarrier(int amount, TimeUnit timeUnit)
      throws BrokenBarrierException, InterruptedException, TimeoutException {
    JaxRsTestResource.BARRIER.await(amount, timeUnit);
  }
}
