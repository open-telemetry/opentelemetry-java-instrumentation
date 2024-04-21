/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package listener;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class TestListener {

  @JmsListener(destination = "SpringListenerJms2")
  void receiveMessage(String message) {
    System.out.println("received: " + message);
  }
}
