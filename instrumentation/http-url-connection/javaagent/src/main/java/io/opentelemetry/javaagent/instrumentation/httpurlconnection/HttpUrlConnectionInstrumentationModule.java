/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.EarlyInstrumentationModule;
import java.util.List;

@AutoService({InstrumentationModule.class, EarlyInstrumentationModule.class})
public class HttpUrlConnectionInstrumentationModule extends InstrumentationModule
    implements EarlyInstrumentationModule {

  public HttpUrlConnectionInstrumentationModule() {
    super("http-url-connection");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpUrlConnectionInstrumentation());
  }
}
