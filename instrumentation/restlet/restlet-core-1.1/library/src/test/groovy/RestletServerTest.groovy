/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.restlet.v1_1.RestletHttpServerTracer.tracer

import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.instrumentation.restlet.v1_1.AbstractRestletServerTest
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.restlet.Filter
import org.restlet.Restlet
import org.restlet.data.Request
import org.restlet.data.Response

class RestletServerTest extends AbstractRestletServerTest implements LibraryTestTrait {

  @Override
  Restlet wrapRestlet(Restlet restlet, String path){
    def filter = new TracingFilter(path)
    filter.setNext(restlet)
    return filter
  }

  static class TracingFilter extends Filter {

    private Scope scope
    private String path

    TracingFilter(String path){
      this.path = path
    }

    @Override
    protected int beforeHandle(Request request, Response response) {

      Context serverContext = tracer().getServerContext(request)

      if(serverContext != null){
        return CONTINUE
      }

      serverContext = tracer().startSpan(request, request, request, path)
      scope = serverContext.makeCurrent()

      return CONTINUE
    }

    @Override
    protected void afterHandle(Request request, Response response) {

      Context serverContext = tracer().getServerContext(request)

      if(scope == null){
        return
      }

      scope.close()

      if(serverContext == null) {
        return
      }

      Throwable statusThrowable = response.getStatus().getThrowable()

      if(statusThrowable != null) {
        tracer().endExceptionally(serverContext, statusThrowable, response)
        return
      }

      tracer().end(serverContext, response)
    }

  }

  @Override
  boolean testException() {
    false //afterHandle does not execute if exception was thrown in the next restlet
  }
}
