/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.eclipse.jetty.continuation.Continuation
import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
//    @ClosureParams(value = SimpleType, options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
//    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
//    final Closure spec) {
//
//    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
//    TEST_WRITER.waitForTraces(size * 3)
//    // TEST_WRITER is a CopyOnWriteArrayList, which doesn't support remove()
//    def toRemove = TEST_WRITER.findAll {
//      it.size() == 1 && it.get(0).name == "TEST_SPAN"
//    }
//    toRemove.each {
//      assertTrace(it, 1) {
//        basicSpan(it, 0, "TEST_SPAN")
//      }
//    }
//    assert toRemove.size() == size * 2
//    TEST_WRITER.removeAll(toRemove)
//
//    assertTraces(size, spec)
//  }
}
