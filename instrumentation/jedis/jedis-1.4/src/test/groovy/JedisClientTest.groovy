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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import io.opentelemetry.trace.attributes.StringAttributeSetter
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

class JedisClientTest extends AgentTestRunner {

  @Shared
  int port = PortUtils.randomOpenPort()

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind 127.0.0.1")
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(port).build()
  @Shared
  Jedis jedis = new Jedis("localhost", port)

  def setupSpec() {
    println "Using redis: $redisServer.args"
    redisServer.start()
  }

  def cleanupSpec() {
    redisServer.stop()
//    jedis.close()  // not available in the early version we're using.
  }

  def setup() {
    jedis.flushAll()
    TEST_WRITER.clear()
  }

  def "set command"() {
    when:
    jedis.set("foo", "bar")

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SET"
          spanKind CLIENT
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${StringAttributeSetter.create("db.connection_string").key()}" "localhost:$port"
            "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
          }
        }
      }
    }
  }

  def "get command"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.get("foo")

    then:
    value == "bar"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "SET"
          spanKind CLIENT
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${StringAttributeSetter.create("db.connection_string").key()}" "localhost:$port"
            "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "GET"
          spanKind CLIENT
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${StringAttributeSetter.create("db.connection_string").key()}" "localhost:$port"
            "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
          }
        }
      }
    }
  }

  def "command with no arguments"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.randomKey()

    then:
    value == "foo"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "SET"
          spanKind CLIENT
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${StringAttributeSetter.create("db.connection_string").key()}" "localhost:$port"
            "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "RANDOMKEY"
          spanKind CLIENT
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${StringAttributeSetter.create("db.connection_string").key()}" "localhost:$port"
            "${SemanticAttributes.DB_STATEMENT.key()}" "RANDOMKEY"
          }
        }
      }
    }
  }
}
