/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery.ArteryInstrumentations;
import io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic.ClassicInstrumentations;
import java.util.ArrayList;
import java.util.List;

/**
 * Propagates context with remote messages sent between Pekko cluster nodes. Artery uses the
 * {@code RemoteInstrument} SPI, while classic remoting appends an unknown protobuf field. The
 * received context is restored before dispatch so Pekko actor instrumentation can carry it to the
 * receiving actor.
 */
@AutoService(InstrumentationModule.class)
public class PekkoRemoteInstrumentationModule extends InstrumentationModule {

  public PekkoRemoteInstrumentationModule() {
    super("pekko-remote", "pekko-remote-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    List<TypeInstrumentation> instrumentations = new ArrayList<>(ArteryInstrumentations.get());
    instrumentations.addAll(ClassicInstrumentations.get());
    return instrumentations;
  }
}
