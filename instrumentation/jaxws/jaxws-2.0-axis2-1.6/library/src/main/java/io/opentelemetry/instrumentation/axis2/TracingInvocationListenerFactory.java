/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.axis2;

import org.apache.axis2.jaxws.core.MessageContext;
import org.apache.axis2.jaxws.server.InvocationListener;
import org.apache.axis2.jaxws.server.InvocationListenerBean;
import org.apache.axis2.jaxws.server.InvocationListenerFactory;

public class TracingInvocationListenerFactory implements InvocationListenerFactory {
  @Override
  public InvocationListener createInvocationListener(MessageContext messageContext) {
    return new TracingInvocationListener(messageContext);
  }

  static class TracingInvocationListener implements InvocationListener {
    private final MessageContext messageContext;

    TracingInvocationListener(MessageContext messageContext) {
      this.messageContext = messageContext;
    }

    @Override
    public void notify(InvocationListenerBean invocationListenerBean) {
      switch (invocationListenerBean.getState()) {
        case REQUEST:
          Axis2Helper.start(messageContext);
          break;
        case RESPONSE:
          Axis2Helper.end(messageContext, null);
          break;
        default:
      }
    }

    @Override
    public void notifyOnException(InvocationListenerBean invocationListenerBean) {
      Axis2Helper.end(messageContext, invocationListenerBean.getThrowable());
    }
  }
}
