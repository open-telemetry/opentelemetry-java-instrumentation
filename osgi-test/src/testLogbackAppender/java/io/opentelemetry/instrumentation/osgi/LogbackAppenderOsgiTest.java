/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.osgi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.test.junit5.context.BundleContextExtension;

@ExtendWith(BundleContextExtension.class)
class LogbackAppenderOsgiTest {

  // Instantiating the appender exercises its class hierarchy and (transitively) confirms that the
  // bundle resolved without the optional net.logstash.logback packages present at runtime.
  @Test
  void appenderInstantiatesWithoutLogstash() {
    Appender<ILoggingEvent> appender = new OpenTelemetryAppender();
    assertInstanceOf(OpenTelemetryAppender.class, appender);
    // logstash-logback-encoder must not be on the runtime classpath for this suite.
    assertNull(getClass().getClassLoader().getResource("net/logstash/logback"));
  }
}
