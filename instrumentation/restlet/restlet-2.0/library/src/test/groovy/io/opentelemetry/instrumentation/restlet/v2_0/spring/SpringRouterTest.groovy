/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.spring

class SpringRouterTest extends AbstractSpringServerLibraryTest {

  @Override
  String getConfigurationName() {
    return "springRouterConf.xml"
  }

}
