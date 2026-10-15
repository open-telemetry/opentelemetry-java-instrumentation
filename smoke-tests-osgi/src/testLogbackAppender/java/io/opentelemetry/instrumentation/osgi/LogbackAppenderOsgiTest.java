/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.osgi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.test.junit5.context.BundleContextExtension;

@ExtendWith(BundleContextExtension.class)
class LogbackAppenderOsgiTest {

  // The suite resolves the appender bundle with logstash-logback-encoder absent (it's a compileOnly
  // dependency, so never on this suite's runtime classpath). The bundle resolving at all proves the
  // net.logstash.logback optional-import tuning works; constructing the appender then exercises its
  // class hierarchy inside the container.
  @Test
  void appenderInstantiatesWithoutLogstash() {
    Appender<ILoggingEvent> appender = new OpenTelemetryAppender();
    assertInstanceOf(OpenTelemetryAppender.class, appender);
  }
}
