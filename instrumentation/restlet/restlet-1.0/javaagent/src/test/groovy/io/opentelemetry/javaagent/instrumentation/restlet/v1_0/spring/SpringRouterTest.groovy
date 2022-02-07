/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_0.spring

import io.opentelemetry.instrumentation.restlet.v1_0.spring.AbstractSpringServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class SpringRouterTest extends AbstractSpringServerTest implements AgentTestTrait {
  @Override
  String getConfigurationName() {
    return "springRouterConf.xml"
  }

}
