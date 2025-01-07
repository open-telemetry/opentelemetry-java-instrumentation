/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_1.spring;

class SpringBeanRouterTest extends AbstractSpringServerLibraryTest {

  @Override
  protected String getConfigurationName() {
    return "springBeanRouterConf.xml";
  }
}
