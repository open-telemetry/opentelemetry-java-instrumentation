/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class PekkoServerIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder) {
    // A stream wake-up can process the next request, which must not inherit the sender's context.
    builder.ignoreTaskClass("org.apache.pekko.stream.impl.fusing.ActorGraphInterpreter$AsyncInput");
    builder.ignoreTaskClass("org.apache.pekko.stream.impl.fusing.GraphInterpreterShell$AsyncInput");
  }
}
