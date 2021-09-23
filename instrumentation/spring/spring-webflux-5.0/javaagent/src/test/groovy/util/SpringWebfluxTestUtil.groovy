/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package util

import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.await

class SpringWebfluxTestUtil {

  // Spring webflux tests complete before all request processing threads have completed. This creates
  // false positive notifications for leaked scopes when strict context checks are enabled. Here we
  // wait for the request processing code to exit and close its scopes to avoid failing strict context
  // check.
  static waitForRequestsToComplete() {
    await()
      .atMost(15, TimeUnit.SECONDS)
      .until({ !isRequestRunning() })
  }

  static boolean isRequestRunning() {
    def result = Thread.getAllStackTraces().values().find { stackTrace ->
      def element = stackTrace.find {
        return ((it.className == "reactor.ipc.netty.http.server.HttpServerOperations" && it.methodName == "onHandlerStart")
          || (it.className == "io.netty.channel.nio.NioEventLoop" && it.methodName == "processSelectedKey"))
      }
      element != null
    }
    return result != null
  }
}
