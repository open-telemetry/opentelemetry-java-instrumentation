/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.event;

import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.ApplicationConfigRunner;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.ClassPathXmlApplicationContext;

class XmlConfigCustomSpanEventTest extends CustomSpanEventTest {

  @RegisterExtension
  static final ApplicationConfigRunner runner =
      new ApplicationConfigRunner(() -> new ClassPathXmlApplicationContext("spring-batch.xml"));

  XmlConfigCustomSpanEventTest() {
    super(runner);
  }
}
