/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.cdi;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.environment.se.threading.RunnableDecorator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CdiContainerTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  public void cdiContainerStartsWithAgent() {
    Weld builder =
        new Weld()
            .disableDiscovery()
            .addDecorator(RunnableDecorator.class)
            .addBeanClass(TestBean.class);
    WeldContainer container = builder.initialize();

    assertThat(container.isRunning()).isTrue();
    if (container != null) {
      container.shutdown();
    }
  }
}
