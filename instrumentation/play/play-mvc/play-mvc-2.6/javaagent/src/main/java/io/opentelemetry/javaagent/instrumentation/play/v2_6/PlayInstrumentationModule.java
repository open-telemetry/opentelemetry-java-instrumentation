/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PlayInstrumentationModule extends InstrumentationModule {

  static final String INSTRUMENTATION_NAME = "play-mvc";
  static final String INSTRUMENTATION_NAME_EXTRA = "play-mvc-2.6";

  public PlayInstrumentationModule() {
    super(INSTRUMENTATION_NAME, INSTRUMENTATION_NAME_EXTRA);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ActionInstrumentation());
  }
}
