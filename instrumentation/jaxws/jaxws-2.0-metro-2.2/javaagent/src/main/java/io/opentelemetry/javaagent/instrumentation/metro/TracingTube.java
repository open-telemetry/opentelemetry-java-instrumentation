/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

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

  @Override
  public NextAction processRequest(Packet request) {
    MetroHelper.start(endpoint, request);

    return super.processRequest(request);
  }

  @Override
  public NextAction processResponse(Packet response) {
    MetroHelper.end(response);

    return super.processResponse(response);
  }

  // this is not used for handling exceptions thrown from webservice invocation
  @Override
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
      MetroHelper.end(request, throwable);
    }

    return super.processException(throwable);
  }
}
