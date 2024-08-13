/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringCloudStreamProducerTest extends AbstractSpringCloudStreamProducerTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  public SpringCloudStreamProducerTest() {
    super(testing, GlobalInterceptorWithProducerSpanSpringConfig.class);
  }
}
