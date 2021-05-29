/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * JAX-RS Client API doesn't define a good point where we can handle connection failures, so we must
 * handle these errors at the implementation level.
 */
@AutoService(InstrumentationModule.class)
public class CxfClientInstrumentationModule extends InstrumentationModule {

  public CxfClientInstrumentationModule() {
    super("jaxrs-client", "jaxrs-client-2.0", "cxf-client", "cxf-client-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CxfClientConnectionErrorInstrumentation(),
        new CxfAsyncClientConnectionErrorInstrumentation());
  }
}
