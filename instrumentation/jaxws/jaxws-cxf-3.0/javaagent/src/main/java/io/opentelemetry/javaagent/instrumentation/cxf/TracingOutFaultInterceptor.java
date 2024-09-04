/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class TracingOutFaultInterceptor extends AbstractPhaseInterceptor<Message> {
  public TracingOutFaultInterceptor() {
    super(Phase.SETUP);
  }

  @Override
  public void handleMessage(Message message) {
    CxfHelper.end(message);
  }
}
