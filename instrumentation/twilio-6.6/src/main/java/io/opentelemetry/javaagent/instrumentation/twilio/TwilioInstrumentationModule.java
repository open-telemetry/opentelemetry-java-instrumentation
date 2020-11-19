/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class TwilioInstrumentationModule extends InstrumentationModule {
  public TwilioInstrumentationModule() {
    super("twilio", "twilio-6.6");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".TwilioClientDecorator",
      packageName + ".TwilioAsyncInstrumentation$SpanFinishingCallback",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new TwilioAsyncInstrumentation(), new TwilioSyncInstrumentation());
  }
}
