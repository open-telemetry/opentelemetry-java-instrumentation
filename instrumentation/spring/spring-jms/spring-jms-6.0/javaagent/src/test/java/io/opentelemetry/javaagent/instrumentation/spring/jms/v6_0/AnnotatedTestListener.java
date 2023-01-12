/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;

import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class AnnotatedTestListener {

  private final CompletableFuture<String> receivedMessage;

  @Autowired
  public AnnotatedTestListener(CompletableFuture<String> receivedMessage) {
    this.receivedMessage = receivedMessage;
  }

  @JmsListener(destination = "spring-jms-listener")
  public void receiveMessage(String message) {
    runWithSpan("consumer", () -> receivedMessage.complete(message));
  }
}
