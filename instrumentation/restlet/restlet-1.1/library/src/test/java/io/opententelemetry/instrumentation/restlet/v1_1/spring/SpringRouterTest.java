/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_1.spring;

class SpringRouterTest extends AbstractSpringServerLibraryTest {

  @Override
  protected String getConfigurationName() {
    return "springRouterConf.xml";
  }
}
