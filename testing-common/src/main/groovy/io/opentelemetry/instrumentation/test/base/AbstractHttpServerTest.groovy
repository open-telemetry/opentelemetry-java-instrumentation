/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base

import ch.qos.logback.classic.Level
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import io.opentelemetry.instrumentation.test.utils.PortUtils
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared

abstract class AbstractHttpServerTest<SERVER> extends AgentTestRunner {
  public static final Logger SERVER_LOGGER = LoggerFactory.getLogger("http-server")
  static {
    ((Logger) SERVER_LOGGER).setLevel(Level.DEBUG)
  }
  protected static final String TEST_CLIENT_IP = "1.1.1.1"
  protected static final String TEST_USER_AGENT = "test-user-agent"

  @Shared
  SERVER server
  @Shared
  OkHttpClient client = OkHttpUtils.client()
  @Shared
  int port
  @Shared
  URI address

  def setupSpec() {
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

  def cleanupSpec() {
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
