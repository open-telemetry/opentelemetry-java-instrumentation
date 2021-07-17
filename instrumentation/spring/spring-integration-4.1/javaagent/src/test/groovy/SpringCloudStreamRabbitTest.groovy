/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait

class SpringCloudStreamRabbitTest extends AbstractSpringCloudStreamRabbitTest implements AgentTestTrait {
  @Override
  Class<?> additionalContextClass() {
    null
  }
}
