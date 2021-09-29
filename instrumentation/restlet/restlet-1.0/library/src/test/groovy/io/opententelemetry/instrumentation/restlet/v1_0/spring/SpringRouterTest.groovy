/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opententelemetry.instrumentation.restlet.v1_0.spring

class SpringRouterTest extends AbstractSpringTest {

  @Override
  String getConfigurationName() {
    return "springRouterConf.xml"
  }

}
