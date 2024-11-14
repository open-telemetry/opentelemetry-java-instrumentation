/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.event;

import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.ApplicationConfigRunner;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.SpringBatchApplication;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class JavaConfigCustomSpanEventTest extends CustomSpanEventTest {

  @RegisterExtension
  static final ApplicationConfigRunner runner =
      new ApplicationConfigRunner(
          () -> new AnnotationConfigApplicationContext(SpringBatchApplication.class));

  JavaConfigCustomSpanEventTest() {
    super(runner);
  }
}
