/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import boot.AbstractSpringBootBasedTest

class SpringBootBasedTest extends AbstractSpringBootBasedTest {

  Class<?> securityConfigClass() {
    SecurityConfig
  }

  @Override
  int getResponseCodeOnNonStandardHttpMethod() {
    Boolean.getBoolean("testLatestDeps") ? 500 : 200
  }
}
