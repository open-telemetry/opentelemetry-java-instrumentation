/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_0.spring

class SpringBeanRouterTest extends AbstractSpringTest {
  @Override
  String getConfigurationName() {
    return "springBeanRouterConf.xml"
  }

}
