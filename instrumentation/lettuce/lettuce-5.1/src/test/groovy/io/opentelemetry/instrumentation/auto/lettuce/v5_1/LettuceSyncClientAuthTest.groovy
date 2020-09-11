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

package io.opentelemetry.instrumentation.auto.lettuce.v5_1

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import redis.embedded.RedisServer
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

class LettuceSyncClientAuthTest extends AgentTestRunner {
  public static final String HOST = "127.0.0.1"
  public static final int DB_INDEX = 0
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  @Shared
  int port
  @Shared
  String password
  @Shared
  String dbAddr
  @Shared
  String embeddedDbUri

  @Shared
  RedisServer redisServer

  RedisClient redisClient

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    dbAddr = HOST + ":" + port + "/" + DB_INDEX
    embeddedDbUri = "redis://" + dbAddr
    password = "password"

    redisServer = RedisServer.builder()
    // bind to localhost to avoid firewall popup
      .setting("bind " + HOST)
    // set max memory to avoid problems in CI
      .setting("maxmemory 128M")
    // Set password
      .setting("requirepass " + password)
      .port(port).build()
  }

  def setup() {
    redisClient = RedisClient.create(embeddedDbUri)
    redisClient.setOptions(CLIENT_OPTIONS)
    redisServer.start()
  }

  def cleanup() {
    redisServer.stop()
  }

  def "auth command"() {
    setup:
    def res =  redisClient.connect().sync().auth(password)

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "AUTH"
          spanKind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key()}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "AUTH"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
      }
    }
  }
}
