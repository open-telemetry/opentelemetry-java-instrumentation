/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

@AutoService(IgnoredTypesConfigurer.class)
public class SpringRabbitIgnoredTypesConfigurer implements IgnoredTypesConfigurer {
  @Override
  public void configure(IgnoredTypesBuilder builder) {
    builder
        .allowClass("org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer")
        .allowClass("org.springframework.amqp.rabbit.listener.BlockingQueueConsumer")
        .allowClass("org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer")
        // contains a Runnable that serves as a worker that continuously reads messages from queue
        .ignoreClass("org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer$")
        .ignoreTaskClass("org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer$")
        // contains consumer callbacks and worker tasks that should not receive generic task tracing
        .ignoreClass("org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer$")
        .ignoreTaskClass("org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer$")
        // a Runnable callback called only on shutdown
        .ignoreClass(
            "org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry$AggregatingCallback");
  }
}
