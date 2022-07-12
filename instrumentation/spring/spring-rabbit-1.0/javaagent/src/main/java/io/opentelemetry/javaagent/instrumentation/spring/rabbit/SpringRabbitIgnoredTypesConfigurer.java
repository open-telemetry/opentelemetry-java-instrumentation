/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

@AutoService(IgnoredTypesConfigurer.class)
public class SpringRabbitIgnoredTypesConfigurer implements IgnoredTypesConfigurer {
  @Override
  public void configure(ConfigProperties config, IgnoredTypesBuilder builder) {
    // contains a Runnable that servers as a worker that continuously reads messages from queue
    builder
        .ignoreClass("org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer$")
        .ignoreTaskClass("org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer$")
        // a Runnable callback called only on shutdown
        .ignoreClass(
            "org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry$AggregatingCallback");
  }
}
