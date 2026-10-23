/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import javax.jms.Message;
import javax.jms.MessageListener;

class ChildClassLoaderMessageListener implements MessageListener {

  @Override
  public void onMessage(Message message) {}
}
