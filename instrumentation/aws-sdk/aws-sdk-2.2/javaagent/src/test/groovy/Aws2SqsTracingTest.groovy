/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2SqsTracingTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

class Aws2SqsTracingTest extends AbstractAws2SqsTracingTest implements AgentTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
  }
}
