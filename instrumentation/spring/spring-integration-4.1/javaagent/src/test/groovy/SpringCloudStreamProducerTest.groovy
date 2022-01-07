/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait

class SpringCloudStreamProducerTest extends AbstractSpringCloudStreamProducerTest implements AgentTestTrait {
  @Override
  Class<?> additionalContextClass() {
    null
  }
}
