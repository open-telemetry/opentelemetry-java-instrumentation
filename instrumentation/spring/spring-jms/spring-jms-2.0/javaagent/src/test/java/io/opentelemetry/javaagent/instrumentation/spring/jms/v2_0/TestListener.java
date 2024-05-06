/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import ch.qos.logback.classic.Level;
import io.opentelemetry.instrumentation.test.utils.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class TestListener {

  private static final Logger logger = LoggerFactory.getLogger(TestListener.class);

  static {
    LoggerUtils.setLevel(logger, Level.INFO);
  }

  @JmsListener(destination = "SpringListenerJms2")
  void receiveMessage(String message) {
    logger.info("received: {}", message);
  }
}
