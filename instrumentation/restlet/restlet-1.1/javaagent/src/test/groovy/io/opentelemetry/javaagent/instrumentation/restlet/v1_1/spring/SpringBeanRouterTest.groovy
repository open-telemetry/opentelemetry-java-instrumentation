/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_1.spring

import io.opentelemetry.instrumentation.restlet.v1_1.spring.AbstractSpringServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint

class SpringBeanRouterTest extends AbstractSpringServerTest implements AgentTestTrait {
  @Override
  String getConfigurationName() {
    return "springBeanRouterConf.xml"
  }

  @Override
  boolean hasResponseCustomizer(ServerEndpoint endpoint) {
    return true
  }

}
