/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.LibraryTestTrait

class SpringCloudStreamRabbitTest extends AbstractSpringCloudStreamRabbitTest implements LibraryTestTrait {
  @Override
  Class<?> additionalContextClass() {
    GlobalInterceptorSpringConfig
  }
}
