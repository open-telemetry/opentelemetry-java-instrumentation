/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0.spring;

import io.opentelemetry.instrumentation.restlet.v2_0.spring.AbstractSpringServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringBeanRouterTest extends AbstractSpringServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHasResponseCustomizer((endpoint) -> true);
  }

  @Override
  protected String getConfigurationName() {
    return "springBeanRouterConf.xml";
  }
}
