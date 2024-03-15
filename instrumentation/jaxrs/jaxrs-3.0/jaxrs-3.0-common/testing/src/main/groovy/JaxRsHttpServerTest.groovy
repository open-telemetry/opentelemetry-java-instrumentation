/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.TimeUnit
import test.JaxRsTestResource

abstract class JaxRsHttpServerTest<S> extends AbstractJaxRsHttpServerTest<S> {

  def setup() {
    // reset the barrier to avoid a failing test breaking subsequent tests
    JaxRsTestResource.BARRIER.reset()
  }

  @Override
  void awaitBarrier(int amount, TimeUnit timeUnit) {
    JaxRsTestResource.BARRIER.await(amount, timeUnit)
  }
}
