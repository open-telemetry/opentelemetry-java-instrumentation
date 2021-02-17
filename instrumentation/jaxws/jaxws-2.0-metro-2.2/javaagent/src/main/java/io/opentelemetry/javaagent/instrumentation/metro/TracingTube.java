/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import static io.opentelemetry.javaagent.instrumentation.metro.MetroJaxWsTracer.tracer;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.Fiber;
import com.sun.xml.ws.api.pipe.NextAction;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.api.pipe.TubeCloner;
import com.sun.xml.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.ws.api.server.WSEndpoint;

public class TracingTube extends AbstractFilterTubeImpl {
  private final WSEndpoint endpoint;

  public TracingTube(WSEndpoint endpoint, Tube next) {
    super(next);
    this.endpoint = endpoint;
  }

  public TracingTube(TracingTube that, TubeCloner tubeCloner) {
    super(that, tubeCloner);
    this.endpoint = that.endpoint;
  }

  @Override
  public AbstractTubeImpl copy(TubeCloner tubeCloner) {
    return new TracingTube(this, tubeCloner);
  }

  public NextAction processRequest(Packet request) {
    tracer().startSpan(endpoint, request);

    return super.processRequest(request);
  }

  public NextAction processResponse(Packet response) {
    tracer().end(response);

    return super.processResponse(response);
  }

  // this is not used for handling exceptions thrown from webservice invocation
  public NextAction processException(Throwable throwable) {
    Packet request = null;
    // we expect this to be called with attached fiber
    // if fiber is not attached current() throws IllegalStateException
    try {
      request = Fiber.current().getPacket();
    } catch (IllegalStateException ignore) {
      // fiber not available
    }
    if (request != null) {
      tracer().end(request, throwable);
    }

    return super.processException(throwable);
  }
}
