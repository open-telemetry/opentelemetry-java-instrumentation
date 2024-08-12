/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SpringCloudStreamRabbitTest extends AbstractSpringCloudStreamRabbitTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  public SpringCloudStreamRabbitTest() {
    super(testing, GlobalInterceptorSpringConfig.class);
  }
}
