/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.filter

import filter.AbstractServletFilterTest
import test.boot.SecurityConfig

class ServletFilterTest extends AbstractServletFilterTest {

  Class<?> securityConfigClass() {
    SecurityConfig
  }

  Class<?> filterConfigClass() {
    ServletFilterConfig
  }
}
