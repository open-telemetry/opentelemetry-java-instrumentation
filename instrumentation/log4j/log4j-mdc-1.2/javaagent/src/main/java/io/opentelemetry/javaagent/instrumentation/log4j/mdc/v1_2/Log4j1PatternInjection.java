/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static java.util.Collections.emptyMap;

import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection.Framework;
import java.util.Map;
import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggingEvent;

public final class Log4j1PatternInjection {
  public static final Framework FRAMEWORK = framework();

  private static Framework framework() {
    // Probe the actual converter once, without emitting a log or touching application MDC.
    // Class-presence checks are ambiguous when an older core is used with log4j-extras.
    StringBuffer result = new StringBuffer();
    new PatternParser("%X").parse().format(result, new ProbeEvent());
    return "{}".equals(result.toString()) ? Framework.LOG4J1_WITH_MDC_MAP : Framework.LOG4J1;
  }

  private static final class ProbeEvent extends LoggingEvent {
    private static final long serialVersionUID = 1L;

    private ProbeEvent() {
      super("", new ProbeCategory(), Level.INFO, "", null);
    }

    @Override
    public Object getMDC(String key) {
      return "single-key";
    }

    // This method is absent from the earliest supported Log4j releases.
    @SuppressWarnings({
      "MissingOverride",
      "UnusedMethod",
      "MethodCanBeStatic",
      "EffectivelyPrivate"
    })
    public Map<String, Object> getProperties() {
      return emptyMap();
    }
  }

  private static final class ProbeCategory extends Category {
    private ProbeCategory() {
      super("otel-pattern-probe");
    }
  }

  private Log4j1PatternInjection() {}
}
