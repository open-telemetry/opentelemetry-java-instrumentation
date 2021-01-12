/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3

import com.linecorp.armeria.client.WebClientBuilder
import com.linecorp.armeria.server.ServerBuilder
import io.opentelemetry.instrumentation.armeria.v1_3.AbstractArmeriaTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class ArmeriaTest extends AbstractArmeriaTest implements AgentTestTrait {
  @Override
  ServerBuilder configureServer(ServerBuilder sb) {
    return sb
  }

  @Override
  WebClientBuilder configureClient(WebClientBuilder clientBuilder) {
    return clientBuilder
  }

  def childSetupSpec() {
    server.before()
  }

  def childCleanupSpec() {
    server.after()
  }
}
