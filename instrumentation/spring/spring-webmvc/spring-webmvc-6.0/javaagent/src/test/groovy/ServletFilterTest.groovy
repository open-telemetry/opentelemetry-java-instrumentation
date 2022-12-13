/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import filter.AbstractServletFilterTest

class ServletFilterTest extends AbstractServletFilterTest {

  Class<?> securityConfigClass() {
    SecurityConfig
  }

  Class<?> filterConfigClass() {
    ServletFilterConfig
  }
}
