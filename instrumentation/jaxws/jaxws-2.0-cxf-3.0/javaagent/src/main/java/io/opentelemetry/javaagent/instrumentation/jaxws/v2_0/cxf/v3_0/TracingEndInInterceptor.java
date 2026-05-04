/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.cxf.v3_0;

import org.apache.cxf.interceptor.OutgoingChainInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class TracingEndInInterceptor extends AbstractPhaseInterceptor<Message> {
  public TracingEndInInterceptor() {
    super(Phase.POST_INVOKE);
    // end the span before the OutInterceptors (callbacks) are executed
    addBefore(OutgoingChainInterceptor.class.getName());
  }

  @Override
  public void handleMessage(Message message) {
    CxfHelper.end(message);
  }
}
