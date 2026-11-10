/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import ch.qos.logback.classic.pattern.MDCConverter;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection.Framework;
import java.util.Map;

public final class LogbackPatternInjection {
  public static final Framework FRAMEWORK = framework();

  private static Framework framework() {
    // Older Logback versions read the default delimiter as part of the key. Probe the
    // native converter once without emitting a log or touching application MDC.
    MDCConverter converter = new MDCConverter();
    converter.setOptionList(singletonList("key:-fallback"));
    converter.start();
    String result = converter.convert(new ProbeEvent());
    converter.stop();
    return "value".equals(result) ? Framework.LOGBACK : Framework.LOGBACK_LEGACY;
  }

  private static final class ProbeEvent extends LoggingEvent {
    @Override
    public Map<String, String> getMDCPropertyMap() {
      return singletonMap("key", "value");
    }
  }

  private LogbackPatternInjection() {}
}
