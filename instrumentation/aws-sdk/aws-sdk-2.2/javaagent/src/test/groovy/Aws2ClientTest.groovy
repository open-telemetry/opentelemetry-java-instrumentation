/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2ClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

class Aws2ClientTest extends AbstractAws2ClientTest implements AgentTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
  }
}
