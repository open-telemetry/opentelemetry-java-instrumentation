/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;

class ChildClassLoaderMessageListener implements MessageListener {

  @Override
  public void onMessage(Message message) {}
}
