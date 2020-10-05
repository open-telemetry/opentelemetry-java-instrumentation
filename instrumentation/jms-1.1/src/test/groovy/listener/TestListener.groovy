/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package listener

import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Component

@Component
class TestListener {

  @JmsListener(destination = "SpringListenerJMS1", containerFactory = "containerFactory")
  void receiveMessage(String message) {
    println "received: " + message
  }
}
