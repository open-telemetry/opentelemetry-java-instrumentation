/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3

import com.linecorp.armeria.server.ServerBuilder
import io.opentelemetry.instrumentation.armeria.v1_3.AbstractArmeriaHttpServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class ArmeriaHttpServerTest extends AbstractArmeriaHttpServerTest implements AgentTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder sb) {
    return sb
  }
}
