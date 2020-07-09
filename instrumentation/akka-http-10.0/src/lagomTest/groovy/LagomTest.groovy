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

import akka.NotUsed
import akka.stream.javadsl.Source
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.javadsl.TestSink
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes
import play.inject.guice.GuiceApplicationBuilder
import spock.lang.Shared

import java.util.function.Function

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup
import static com.lightbend.lagom.javadsl.testkit.ServiceTest.startServer
import static io.opentelemetry.trace.Span.Kind.SERVER

class LagomTest extends AgentTestRunner {

  @Shared
  private TestServer server

  def setupSpec() {
    server = startServer(defaultSetup()
      .withCluster(false)
      .withPersistence(false)
      .withCassandra(false)
      .withJdbc(false)
      .configureBuilder(
        new Function<GuiceApplicationBuilder, GuiceApplicationBuilder>() {
          @Override
          GuiceApplicationBuilder apply(GuiceApplicationBuilder builder) {
            return builder
              .bindings(new ServiceTestModule())
          }
        }))
  }

  def cleanupSpec() {
    server.stop()
  }

  def "normal request traces"() {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
        .concat(Source.maybe())
    Source<String, NotUsed> output = service.echo().invoke(input)
      .toCompletableFuture().get()
    Probe<String> probe = output.runWith(TestSink.probe(server.system()), server.materializer())
    probe.request(10)
    probe.expectNext("msg1")
    probe.expectNext("msg2")
    probe.expectNext("msg3")
    probe.cancel()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName expectedOperationName()
          spanKind SERVER
          errored false
          attributes {
            "${SemanticAttributes.HTTP_URL.key()}" "ws://localhost:${server.port()}/echo"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 101
          }
        }
        span(1) {
          childOf span(0)
          operationName 'tracedMethod'
          attributes {
          }
        }
      }
    }
  }

  def "error traces"() {
    setup:
    EchoService service = server.client(EchoService)

    Source<String, NotUsed> input =
      Source.from(Arrays.asList("msg1", "msg2", "msg3"))
        .concat(Source.maybe())
    try {
      service.error().invoke(input).toCompletableFuture().get()
    } catch (Exception e) {
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName expectedOperationName()
          spanKind SERVER
          errored true
          attributes {
            "${SemanticAttributes.HTTP_URL.key()}" "ws://localhost:${server.port()}/error"
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 500
          }
        }
      }
    }
  }

  String expectedOperationName() {
    return "akka.request"
  }
}
