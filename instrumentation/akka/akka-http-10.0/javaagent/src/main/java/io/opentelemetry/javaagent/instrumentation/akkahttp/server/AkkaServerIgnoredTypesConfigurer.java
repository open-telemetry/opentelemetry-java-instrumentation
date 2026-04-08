/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class AkkaServerIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder) {
    // in AkkaHttpServerInstrumentationTestAsync http pipeline test sending this message trigger
    // processing next request, we don't want to propagate context there
    builder.ignoreTaskClass("akka.stream.impl.fusing.ActorGraphInterpreter$AsyncInput");
  }
}
