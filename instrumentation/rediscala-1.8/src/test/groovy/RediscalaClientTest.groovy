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

import akka.actor.ActorSystem
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import redis.ByteStringDeserializerDefault
import redis.ByteStringSerializerLowPriority
import redis.RedisClient
import redis.RedisDispatcher
import redis.embedded.RedisServer
import scala.Option
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

class RediscalaClientTest extends AgentTestRunner {

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
  ActorSystem system

  @Shared
  RedisClient redisClient

  def setupSpec() {
    system = ActorSystem.create()
    redisClient = new RedisClient("localhost",
      port,
      Option.apply(null),
      Option.apply(null),
      "RedisClient",
      Option.apply(null),
      system,
      new RedisDispatcher("rediscala.rediscala-client-worker-dispatcher"))

    println "Using redis: $redisServer.args"
    redisServer.start()
  }

  def cleanupSpec() {
    redisServer.stop()
    system?.terminate()
  }

  def "set command"() {
    when:
    def value = redisClient.set("foo",
      "bar",
      Option.apply(null),
      Option.apply(null),
      false,
      false,
      new ByteStringSerializerLowPriority.String$())


    then:
    Await.result(value, Duration.apply("3 second")) == true
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "Set"
          spanKind CLIENT
          tags {
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "Set"
          }
        }
      }
    }
  }

  def "get command"() {
    when:
    def write = redisClient.set("bar",
      "baz",
      Option.apply(null),
      Option.apply(null),
      false,
      false,
      new ByteStringSerializerLowPriority.String$())
    def value = redisClient.get("bar", new ByteStringDeserializerDefault.String$())

    then:
    Await.result(write, Duration.apply("3 second")) == true
    Await.result(value, Duration.apply("3 second")) == Option.apply("baz")
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          operationName "Set"
          spanKind CLIENT
          tags {
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "Set"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "Get"
          spanKind CLIENT
          tags {
            "$Tags.DB_TYPE" "redis"
            "$Tags.DB_STATEMENT" "Get"
          }
        }
      }
    }
  }
}
