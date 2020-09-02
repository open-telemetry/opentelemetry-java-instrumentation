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
import org.redisson.Redisson
import org.redisson.api.*
import org.redisson.config.Config
import redis.embedded.RedisServer
import spock.lang.Shared

import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

import static io.opentelemetry.trace.Span.Kind.CLIENT

class RedissonAsyncClientTest extends AgentTestRunner {

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
  RedissonClient redisson
  @Shared
  String address = "localhost:" + port

  def setupSpec() {
    if ("true".equals(System.properties.getProperty("testLatestDeps"))) {
      // Newer versions of redisson require scheme, older versions forbid it
      address = "redis://" + address
    }
    println "Using redis: $redisServer.args"
    redisServer.start()
  }

  def cleanupSpec() {
    redisson.shutdown()
    redisServer.stop()
  }

  def setup() {
    Config config = new Config()
    config.useSingleServer().setAddress(address)
    redisson = Redisson.create(config)
    TEST_WRITER.clear()
  }

  def "test future get"() {
    when:
    RBucket<String> keyObject = redisson.getBucket("foo")
    RFuture future = keyObject.setAsync("bar")
    future.get(3, TimeUnit.SECONDS)
    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SET"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
          }
        }
      }
    }
  }

  def "test future whenComplete"() {
    when:
    RSet<String> rSet = redisson.getSet("set1")
    RFuture<Boolean> result = rSet.addAsync("s1")
    result.whenComplete(new BiConsumer<Boolean, Throwable>() {
      @Override
      void accept(Boolean res, Throwable throwable) {
        RList<String> strings = redisson.getList("list1")
        strings.add("a")
      }
    })
    then:
    result.get(3, TimeUnit.SECONDS)
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "SADD"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "SADD"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "RPUSH"
          spanKind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "RPUSH"
          }
        }
      }
    }
  }

}

