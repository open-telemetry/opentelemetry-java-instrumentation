/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class AkkaIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    // This is a Mailbox created by akka.dispatch.Dispatcher#createMailbox. We must not add
    // a context to it as context should only be carried by individual envelopes in the queue
    // of this mailbox.
    builder.ignoreTaskClass("akka.dispatch.Dispatcher$$anon$1");
  }
}
