/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.continuation.Continuation
import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler

// FIXME: We don't currently handle jetty continuations properly (at all).
abstract class JettyContinuationHandlerTest extends JettyHandlerTest {

  @Override
  AbstractHandler handler() {
    ContinuationTestHandler.INSTANCE
  }

  static class ContinuationTestHandler extends AbstractHandler {
    static final ContinuationTestHandler INSTANCE = new ContinuationTestHandler()
    final ExecutorService executorService = Executors.newSingleThreadExecutor()

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      final Continuation continuation = ContinuationSupport.getContinuation(request)
      if (continuation.initial) {
        continuation.suspend()
        executorService.execute {
          continuation.resume()
        }
      } else {
        handleRequest(baseRequest, response)
      }
      baseRequest.handled = true
    }
  }

//  // This server seems to generate a TEST_SPAN twice... once for the initial request, and once for the continuation.
//  void cleanAndAssertTraces(
//    final int size,
//    @ClosureParams(value = SimpleType, options = "io.opentelemetry.instrumentation.test.asserts.ListWriterAssert")
//    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
//    final Closure spec) {
//
//    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
//    testWriter.waitForTraces(size * 3)
//    // testWriter is a CopyOnWriteArrayList, which doesn't support remove()
//    def toRemove = testWriter.findAll {
//      it.size() == 1 && it.get(0).name == "TEST_SPAN"
//    }
//    toRemove.each {
//      assertTrace(it, 1) {
//        basicSpan(it, 0, "TEST_SPAN")
//      }
//    }
//    assert toRemove.size() == size * 2
//    testWriter.removeAll(toRemove)
//
//    assertTraces(size, spec)
//  }
}
