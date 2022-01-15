/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.LibraryTestTrait

class SpringCloudStreamProducerTest extends AbstractSpringCloudStreamProducerTest implements LibraryTestTrait {
  @Override
  Class<?> additionalContextClass() {
    GlobalInterceptorSpringConfig
  }
}
