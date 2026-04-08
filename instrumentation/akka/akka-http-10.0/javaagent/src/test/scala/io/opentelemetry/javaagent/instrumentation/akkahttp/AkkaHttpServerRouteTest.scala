/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.testing.internal.armeria.client.WebClient
import io.opentelemetry.testing.internal.armeria.common.{
  AggregatedHttpRequest,
  HttpMethod
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{AfterAll, Test, TestInstance}
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

import java.net.{URI, URISyntaxException}
import java.util.function.Consumer
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AkkaHttpServerRouteTest {
  @RegisterExtension private val testing: AgentInstrumentationExtension =
    AgentInstrumentationExtension.create
  private val client: WebClient = WebClient.of()

  implicit val system: ActorSystem = ActorSystem("my-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private def buildAddress(port: Int): URI = try
    new URI("http://localhost:" + port + "/")
  catch {
    case exception: URISyntaxException =>
      throw new IllegalStateException(exception)
  }

  @Test def testSimple(): Unit = {
    val route = path("test") {
      complete("ok")
    }

    test(route, "/test", "GET /test")
  }

  @Test def testPathPrefix(): Unit = {
    val route =
      pathPrefix("a") {
        pathPrefix("b") {
          path("c") {
            complete("ok")
          }
        }
      }

    test(route, "/a/b/c", "GET /a/b/c")
  }

  @Test def testTrailingSlash(): Unit = {
    val route = path("foo"./) { complete("ok") }
    test(route, "/foo/", "GET /foo/")
  }

  @Test def testSlash(): Unit = {
    val route = path("foo" / "bar") { complete("ok") }
    test(route, "/foo/bar", "GET /foo/bar")
  }

  @Test def testEncodedSlash(): Unit = {
    val route = path("foo/bar") { complete("ok") }
    test(route, "/foo%2Fbar", "GET /foo%2Fbar")
  }

  @Test def testSeparateOnSlashes(): Unit = {
    val route = path(separateOnSlashes("foo/bar")) { complete("ok") }
    test(route, "/foo/bar", "GET /foo/bar")
  }

  @Test def testMatchRegex(): Unit = {
    val route = path("foo" / """number-\d+""".r) { _ => complete("ok") }
    test(route, "/foo/number-123", "GET /foo/*")
  }

  @ParameterizedTest
  @CsvSource(
    Array(
      "/i42, GET /i*",
      "/hCAFE, GET /h*"
    )
  )
  def testPipe(requestPath: String, expectedName: String): Unit = {
    val route = path("i" ~ IntNumber | "h" ~ HexIntNumber) { _ =>
      complete("ok")
    }
    test(route, requestPath, expectedName)
  }

  @ParameterizedTest
  @CsvSource(
    Array(
      "/colours/red, GET /colours/red",
      "/colours/green, GET /colours/green"
    )
  )
  def testMapExtractor(requestPath: String, expectedName: String): Unit = {
    val route = path("colours" / Map("red" -> 1, "green" -> 2, "blue" -> 3)) {
      _ => complete("ok")
    }
    test(route, requestPath, expectedName)
  }

  @ParameterizedTest
  @CsvSource(
    Array(
      "/fooish, GET /foo*",
      "/fooish/123, GET /foo*"
    )
  )
  def testNotMatch(requestPath: String, expectedName: String): Unit = {
    val route = pathPrefix("foo" ~ !"bar") { complete("ok") }
    test(route, requestPath, expectedName)
  }

  @Test def testProvide(): Unit = {
    val route = pathPrefix("foo") {
      provide("hi") { _ =>
        path("bar") {
          complete("ok")
        }
      }
    }
    test(route, "/foo/bar", "GET /foo/bar")
  }

  @ParameterizedTest
  @CsvSource(
    Array(
      "/foo/bar/X42/edit, GET /foo/bar/X*/edit",
      "/foo/bar/X/edit, GET /foo/bar/X/edit"
    )
  )
  def testOptional(requestPath: String, expectedName: String): Unit = {
    val route = path("foo" / "bar" / "X" ~ IntNumber.? / ("edit" | "create")) {
      _ => complete("ok")
    }
    test(route, requestPath, expectedName)
  }

  @Test def testNoMatches(): Unit = {
    val route = path("foo" / "bar") { complete("ok") }
    test(
      route,
      "/foo/wrong",
      "GET",
      404,
      "The requested resource could not be found."
    )
  }

  @Test def testError(): Unit = {
    val route = path("foo" / IntNumber) { _ =>
      failWith(new RuntimeException("oops"))
    }
    test(
      route,
      "/foo/123",
      "GET /foo/*",
      500,
      "There was an internal server error."
    )
  }

  @Test def testConcat(): Unit = {
    val route = concat(
      pathEndOrSingleSlash {
        complete("root")
      },
      path(".+".r / "wrong1") { _ =>
        complete("wrong1")
      },
      pathPrefix("test") {
        concat(
          pathSingleSlash {
            complete("test")
          },
          pathPrefix("foo") {
            concat(
              path(IntNumber) { _ =>
                complete("ok")
              }
            )
          },
          path("something-else") {
            complete("test")
          }
        )
      },
      path("test" / "wrong2") {
        complete("wrong2")
      }
    )

    test(route, "/test/foo/1", "GET /test/foo/*")
  }

  @Test def testRouteWithUUID(): Unit = {
    val route =
      pathPrefix("foo") {
        pathPrefix("api") {
          pathPrefix("v2") {
            pathPrefix("bar") {
              path(JavaUUID) { _ =>
                complete("ok")
              }
            }
          }
        }
      }

    test(
      route,
      "/foo/api/v2/bar/5bb7c7d8-0128-4349-86af-fe718f4f8059",
      "GET /foo/api/v2/bar/*"
    )
  }

  @Test def testRouteWithSegment(): Unit = {
    val route =
      pathPrefix("api") {
        pathPrefix("v2") {
          pathPrefix("orders") {
            path(Segment) { _ =>
              complete("ok")
            }
          }
        }
      }

    test(route, "/api/v2/orders/order123", "GET /api/v2/orders/*")
  }

  @Test def testRouteWithSubSegment(): Unit = {
    val route =
      pathPrefix("api" / "v2" / "orders" / Segment / "status") { _ =>
        complete("ok")
      }

    test(route, "/api/v2/orders/order123/status", "GET /api/v2/orders/*/status")
  }

  def test(
      route: Route,
      path: String,
      spanName: String,
      expectedStatus: Int = 200,
      expectedMsg: String = "ok"
  ): Unit = {
    val port = PortUtils.findOpenPort
    val address: URI = buildAddress(port)
    val binding =
      Await.result(Http().bindAndHandle(route, "localhost", port), 10.seconds)
    try {
      val request = AggregatedHttpRequest.of(
        HttpMethod.GET,
        address.resolve(path).toString
      )
      val response = client.execute(request).aggregate.join
      assertThat(response.status.code).isEqualTo(expectedStatus)
      assertThat(response.contentUtf8).isEqualTo(expectedMsg)

      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span.hasName(spanName)
            }
          })
      })
    } finally {
      binding.unbind()
    }
  }

  @AfterAll
  def cleanUp(): Unit = {
    system.terminate()
  }
}
