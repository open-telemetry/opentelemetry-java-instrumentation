/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.cxf.v3_0;

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
