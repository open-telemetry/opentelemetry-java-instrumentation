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
}
