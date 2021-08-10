/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package util

import static org.awaitility.Awaitility.await

import java.util.concurrent.TimeUnit

class SpringWebfluxTestUtil {

  static waitForRequestsToComplete() {
    await()
      .atMost(15, TimeUnit.SECONDS)
      .until({ !isRequestRunning() })
  }

  static boolean isRequestRunning() {
    def result = Thread.getAllStackTraces().values().find {stackTrace ->
      def element = stackTrace.find {
        return ((it.className == "reactor.ipc.netty.http.server.HttpServerOperations" && it.methodName == "onHandlerStart")
          || (it.className == "io.netty.channel.nio.NioEventLoop" && it.methodName == "processSelectedKey"))
      }
      element != null
    }
    return result != null
  }
}
