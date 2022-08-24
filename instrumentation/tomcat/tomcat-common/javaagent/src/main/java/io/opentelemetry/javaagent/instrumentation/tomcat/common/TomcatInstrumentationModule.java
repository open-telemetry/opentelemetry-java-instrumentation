/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;

public abstract class TomcatInstrumentationModule extends InstrumentationModule {

  public TomcatInstrumentationModule(
      String mainInstrumentationName, String... additionalInstrumentationNames) {
    super(mainInstrumentationName, additionalInstrumentationNames);
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("org.json");
  }
}
