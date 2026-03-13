/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GoogleHttpClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public GoogleHttpClientInstrumentationModule() {
    super("google_http_client", "google_http_client_1.19");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new GoogleHttpRequestInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
