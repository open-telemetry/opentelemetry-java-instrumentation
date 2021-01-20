/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base

import ch.qos.logback.classic.Level
import io.opentelemetry.instrumentation.test.RetryOnAddressAlreadyInUseTrait
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import io.opentelemetry.instrumentation.test.utils.PortUtils
import okhttp3.OkHttpClient
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A trait for testing requests against http server.
 */
trait HttpServerTestTrait<SERVER> implements RetryOnAddressAlreadyInUseTrait {
  static final Logger SERVER_LOGGER = LoggerFactory.getLogger("http-server")
  static {
    ((ch.qos.logback.classic.Logger) SERVER_LOGGER).setLevel(Level.DEBUG)
  }
  static final String TEST_CLIENT_IP = "1.1.1.1"
  static final String TEST_USER_AGENT = "test-user-agent"

  // not using SERVER as type because it triggers a bug in groovy and java joint compilation
  static Object server
  static OkHttpClient client = OkHttpUtils.client()
  static int port
  static URI address

  @BeforeClass
  def setupServer() {
    withRetryOnAddressAlreadyInUse({
      setupSpecUnderRetry()
    })
  }

  def setupSpecUnderRetry() {
    port = PortUtils.randomOpenPort()
    address = buildAddress()
    server = startServer(port)
    println getClass().name + " http server started at: http://localhost:$port" + getContextPath()
  }

  URI buildAddress() {
    return new URI("http://localhost:$port" + getContextPath() + "/")
  }

  abstract SERVER startServer(int port)

  @AfterClass
  def cleanupServer() {
    if (server == null) {
      println getClass().name + " can't stop null server"
      return
    }
    stopServer(server)
    server = null
    println getClass().name + " http server stopped at: http://localhost:$port/"
  }

  abstract void stopServer(SERVER server)

  String getContextPath() {
    return ""
  }
}
