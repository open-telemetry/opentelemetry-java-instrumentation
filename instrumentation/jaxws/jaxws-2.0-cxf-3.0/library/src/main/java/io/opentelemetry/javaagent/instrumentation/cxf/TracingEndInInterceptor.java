/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import static io.opentelemetry.javaagent.instrumentation.cxf.CxfJaxWsTracer.tracer;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class TracingEndInInterceptor extends AbstractPhaseInterceptor {
  public TracingEndInInterceptor() {
    super(Phase.POST_INVOKE);
  }

  @Override
  public void handleMessage(Message message) {
    tracer().stopSpan(message);
  }
}
