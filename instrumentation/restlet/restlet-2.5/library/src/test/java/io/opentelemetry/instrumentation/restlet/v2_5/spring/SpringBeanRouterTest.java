/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_5.spring;

class SpringBeanRouterTest extends AbstractSpringServerLibraryTest {

  @Override
  protected String getConfigurationName() {
    return "springBeanRouterConf.xml";
  }
}
