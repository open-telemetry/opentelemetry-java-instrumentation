/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, IllegalUriException}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.{Span, SpanKind}
import io.opentelemetry.context.{Context, ContextKey}
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.testing.internal.armeria.common.{
  HttpRequest => ServerRequest,
  HttpResponse => ServerResponse
}
import io.opentelemetry.testing.internal.armeria.server.{
  HttpService,
  Server,
  ServiceRequestContext
}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{Test, TestInstance}
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import java.net.{URL, URLClassLoader}
import java.util.concurrent.{
  CompletableFuture,
  CountDownLatch,
  RejectedExecutionException,
  TimeUnit
}
import java.util.function.Consumer
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.Try

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientCallbackTest {
  @RegisterExtension private val testing: AgentInstrumentationExtension =
    AgentInstrumentationExtension.create()

  private val inlineExecutionContext: ExecutionContext = new ExecutionContext {
    override def execute(command: Runnable): Unit = {
      val scope = Context.root().makeCurrent()
      try command.run()
      finally scope.close()
    }
    override def reportFailure(error: Throwable): Unit = throw error
  }

  @ParameterizedTest
  @ValueSource(booleans = Array(true, false))
  def completedResponseCallbacks(withParent: Boolean): Unit = {
    val system = ActorSystem("completed-client")
    try {
      implicit val materializer: ActorMaterializer =
        ActorMaterializer.create(system)
      val marker = ContextKey.named[String]("callback-caller")
      implicit val executionContext: ExecutionContext = inlineExecutionContext
      val parent =
        if (withParent)
          testing.getOpenTelemetry
            .getTracer("test")
            .spanBuilder("parent")
            .startSpan()
        else Span.getInvalid
      try {
        val scope =
          Context.root().`with`(parent).`with`(marker, "caller").makeCurrent()
        try {
          val response =
            Http.get(system).singleRequest(HttpRequest(uri = "/relative"))
          Await.ready(response, 5.seconds)
          val callback = Promise[Unit]()
          response.onComplete { _ =>
            assertThat(Span.current().getSpanContext)
              .isEqualTo(parent.getSpanContext)
            assertThat(Context.current().get(marker)).isEqualTo("caller")
            callback.success(())
          }
          Await.result(callback.future, 5.seconds)
          val recovered = response.recover { case _: IllegalUriException =>
            assertThat(Span.current().getSpanContext)
              .isEqualTo(parent.getSpanContext)
            assertThat(Context.current().get(marker)).isEqualTo("caller")
            ()
          }
          Await.result(recovered, 5.seconds)
          val recoveredCallback = Promise[String]()
          recovered.onComplete(_ =>
            recoveredCallback.success(Context.current().get(marker))
          )
          assertThat(Await.result(recoveredCallback.future, 5.seconds))
            .isEqualTo("caller")

          val failedCallback = Promise[String]()
          response
            .failed
            .map(_ => Context.current().get(marker))
            .onComplete(_ =>
              failedCallback.success(Context.current().get(marker))
            )
          assertThat(Await.result(failedCallback.future, 5.seconds))
            .isEqualTo("caller")

          val other = Promise[HttpResponse]()
          val recoveredWith = response.recoverWith {
            case _: IllegalUriException =>
              other.future
          }
          val otherCallback = Promise[String]()
          other.future.onComplete(_ =>
            otherCallback.success(Context.current().get(marker))
          )
          other.success(HttpResponse())
          Await.result(recoveredWith, 5.seconds)
          assertThat(Await.result(otherCallback.future, 5.seconds)).isNull()
          // A plain Scala future must retain its normal inline-execution behavior.
          assertThat(
            Await.result(
              Future.successful(1).map(_ => Context.current().get(marker)),
              5.seconds
            )
          ).isNull()
          assertThat(Context.current().get(marker)).isEqualTo("caller")
        } finally {
          scope.close()
        }
      } finally {
        parent.end()
      }
      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit = {
          val client = new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasStatus(StatusData.error())
              if (withParent) span.hasParent(trace.getSpan(0))
              else span.hasNoParent()
            }
          }
          if (withParent)
            trace.hasSpansSatisfyingExactly(
              new Consumer[SpanDataAssert] {
                override def accept(span: SpanDataAssert): Unit =
                  span.hasName("parent").hasNoParent()
              },
              client
            )
          else trace.hasSpansSatisfyingExactly(client)
        }
      })
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def callbackContextWithParentScalaAndChildAkkaHttp(): Unit = {
    val received = new CountDownLatch(1)
    val reply = new CompletableFuture[ServerResponse]()
    val server = Server
      .builder()
      .http(PortUtils.findOpenPort())
      .service(
        "/",
        new HttpService {
          override def serve(
              ctx: ServiceRequestContext,
              request: ServerRequest
          ): ServerResponse = {
            received.countDown()
            ServerResponse.of(reply)
          }
        }
      )
      .build()
    try {
      server.start().join()
      val loader = childAkkaHttpClassLoader()
      val previousContextClassLoader = Thread.currentThread().getContextClassLoader
      var helperClass: Class[_] = null
      var helper: AnyRef = null
      try {
        Thread.currentThread().setContextClassLoader(loader)
        helperClass = Class.forName(childHttpClientClassName, true, loader)
        assertThat(helperClass.getClassLoader).isSameAs(loader)
        assertThat(Class.forName("akka.http.scaladsl.HttpExt", false, loader).getClassLoader)
          .isSameAs(loader)
        assertThat(Class.forName("scala.concurrent.Future", false, loader).getClassLoader)
          .isSameAs(classOf[Future[_]].getClassLoader)

        helper =
          helperClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
        val response = helperClass
          .getMethod("singleRequest", classOf[String])
          .invoke(
            helper,
            "http://localhost:" + server.activeLocalPort() + "/"
          )
          .asInstanceOf[Future[AnyRef]]
        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue()

        val marker = ContextKey.named[String]("parent-scala-child-akka")
        val callback = Promise[String]()
        val scope = Context.root().`with`(marker, "caller").makeCurrent()
        try {
          response.onComplete(_ => callback.success(Context.current().get(marker)))(
            inlineExecutionContext
          )
        } finally {
          scope.close()
        }
        reply.complete(ServerResponse.of("ok"))
        val result = Await.result(response, 5.seconds)
        assertThat(
          helperClass.getMethod("status", classOf[Object]).invoke(helper, result)
        ).isEqualTo(200)
        helperClass
          .getMethod("discardEntity", classOf[Object])
          .invoke(helper, result)
        assertThat(Await.result(callback.future, 5.seconds)).isEqualTo("caller")
      } finally {
        try {
          if (helper != null) helperClass.getMethod("terminate").invoke(helper)
        } finally {
          Thread.currentThread().setContextClassLoader(previousContextClassLoader)
          loader.close()
        }
      }
      testing.waitAndAssertTraces(new Consumer[TraceAssert] {
        override def accept(trace: TraceAssert): Unit =
          trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit =
              span
                .hasName("GET")
                .hasKind(SpanKind.CLIENT)
                .hasNoParent()
          })
      })
    } finally {
      reply.complete(ServerResponse.of("cleanup"))
      server.stop().join()
    }
  }

  @Test
  def failedResponseWithFullAffinityQueue(): Unit = {
    val system = newAffinitySystem()
    try {
      implicit val materializer: ActorMaterializer =
        ActorMaterializer.create(system)
      val http = Http.get(system)
      withFullQueue(system) {
        // Akka fails this request immediately, without network or dispatcher work.
        val response = http.singleRequest(HttpRequest(uri = "/relative"))
        val failure = Await.result(response.failed, 5.seconds)
        assertThat(failure).isInstanceOf(classOf[IllegalUriException])
        val called = new AtomicBoolean()
        response.onComplete(_ => called.set(true))(system.dispatcher)
        assertThat(called.get()).isFalse()
        assertClientTrace(StatusData.error(), failure)
      }
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  @Test
  def successfulResponseWithFullAffinityQueue(): Unit = {
    val system = newAffinitySystem()
    val received = new CountDownLatch(1)
    val reply = new CompletableFuture[ServerResponse]()
    val server = Server
      .builder()
      .http(PortUtils.findOpenPort())
      .service(
        "/",
        new HttpService {
          override def serve(
              ctx: ServiceRequestContext,
              request: ServerRequest
          ): ServerResponse = {
            received.countDown()
            ServerResponse.of(reply)
          }
        }
      )
      .build()
    try {
      server.start().join()
      implicit val materializer: ActorMaterializer =
        ActorMaterializer.create(system)
      val response = Http
        .get(system)
        .singleRequest(
          HttpRequest(uri =
            "http://localhost:" + server.activeLocalPort() + "/"
          )
        )
      assertThat(received.await(10, TimeUnit.SECONDS)).isTrue()
      val marker = ContextKey.named[String]("registration")
      val callbacks = Seq("first", "second", null).map { value =>
        val context =
          if (value == null) Context.root()
          else Context.root().`with`(marker, value)
        val scope = context.makeCurrent()
        try {
          val mapped = response.map { result =>
            assertThat(Context.current().get(marker)).isEqualTo(value)
            result.status.intValue()
          }(inlineExecutionContext)
          val callback = Promise[Unit]()
          mapped.onComplete { result =>
            callback.complete(Try {
              assertThat(result.get).isEqualTo(200)
              assertThat(Context.current().get(marker)).isEqualTo(value)
              ()
            })
          }(inlineExecutionContext)
          callback.future
        } finally {
          scope.close()
        }
      }
      withFullQueue(system) {
        reply.complete(ServerResponse.of("ok"))
        val result = Await.result(response, 5.seconds)
        assertThat(result.status.intValue()).isEqualTo(200)
        result.discardEntityBytes()
        callbacks.foreach(Await.result(_, 5.seconds))
        val projectionScope =
          Context.root().`with`(ContextKey.named[String]("failed-projection"), "registration")
            .makeCurrent()
        try {
          val projected = response.failed
          val callback = Promise[String]()
          projected.onComplete(_ =>
            callback.success(Context.current().get(ContextKey.named[String]("failed-projection")))
          )(inlineExecutionContext)
          assertThat(Await.result(callback.future, 5.seconds))
            .isEqualTo(
              if (java.lang.Boolean.getBoolean("testLatestDeps")) null
              else "registration"
            )
        } finally {
          projectionScope.close()
        }
        val other = Future.failed[Int](new IllegalStateException("other"))
        val scope = Context.root().`with`(marker, "unrelated").makeCurrent()
        try {
          Await.ready(response.zip(other), 5.seconds)
          val callback = Promise[String]()
          other.onComplete(_ =>
            callback.success(Context.current().get(marker))
          )(inlineExecutionContext)
          assertThat(Await.result(callback.future, 5.seconds)).isNull()
        } finally {
          scope.close()
        }
        assertClientTrace(StatusData.unset(), null)
      }
    } finally {
      reply.complete(ServerResponse.of("cleanup"))
      Await.result(system.terminate(), 10.seconds)
      server.stop().join()
    }
  }

  private def assertClientTrace(status: StatusData, failure: Throwable): Unit =
    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(new Consumer[SpanDataAssert] {
          override def accept(span: SpanDataAssert): Unit = {
            span
              .hasName("GET")
              .hasKind(SpanKind.CLIENT)
              .hasNoParent()
              .hasStatus(status)
            if (failure != null) span.hasException(failure)
          }
        })
    })

  private def newAffinitySystem(): ActorSystem = {
    // The affinity executor is not available in the baseline Akka 2.4.
    assumeTrue(java.lang.Boolean.getBoolean("testLatestDeps"))
    ActorSystem(
      "saturated-client",
      ConfigFactory.parseString("""
        akka.actor.default-dispatcher {
          executor = "affinity-pool-executor"
          affinity-pool-executor {
            parallelism-min = 1
            parallelism-max = 1
            task-queue-size = 64
          }
        }
        akka.loggers-dispatcher = "akka.actor.default-blocking-io-dispatcher"
        akka.stream.materializer.dispatcher = "akka.actor.default-blocking-io-dispatcher"
      """)
    )
  }

  private def childAkkaHttpClassLoader(): URLClassLoader = {
    val urls = new java.util.LinkedHashSet[URL]()
    urls.add(getClass.getProtectionDomain.getCodeSource.getLocation)
    Seq("akka", "com/typesafe/sslconfig/akka").foreach { packageName =>
      val resources = getClass.getClassLoader.getResources(packageName)
      while (resources.hasMoreElements) {
        val resource = resources.nextElement()
        if (resource.getProtocol == "jar") {
          urls.add(
            resource
              .openConnection()
              .asInstanceOf[java.net.JarURLConnection]
              .getJarFileURL
          )
        } else if (resource.getProtocol == "file") {
          val path = java.nio.file.Paths.get(resource.toURI)
          urls.add(path.getParent.toUri.toURL)
        }
      }
    }
    new URLClassLoader(urls.toArray(new Array[URL](urls.size())), getClass.getClassLoader) {
      override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
        if (name.startsWith("akka.")
          || name.startsWith("com.typesafe.sslconfig.akka.")
          || name == childHttpClientClassName) {
          var loaded = findLoadedClass(name)
          if (loaded == null) {
            try loaded = findClass(name)
            catch {
              case _: ClassNotFoundException => loaded = super.loadClass(name, false)
            }
          }
          if (resolve) resolveClass(loaded)
          loaded
        } else {
          super.loadClass(name, resolve)
        }
      }
    }
  }

  private val childHttpClientClassName =
    "io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client.ChildLoadedAkkaHttpClient"

  private def withFullQueue(system: ActorSystem)(body: => Unit): Unit = {
    val release = new CountDownLatch(1)
    try {
      val started = new CountDownLatch(1)
      system.dispatcher.execute(new Runnable {
        override def run(): Unit = {
          started.countDown()
          release.await()
        }
      })
      assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
      val noop = new Runnable {
        override def run(): Unit = ()
      }
      var rejected = false
      var submitted = 0
      while (!rejected && submitted < 1000) {
        try {
          system.dispatcher.execute(noop)
          submitted += 1
        } catch {
          case _: RejectedExecutionException => rejected = true
        }
      }
      assertThat(rejected).isTrue()
      body
    } finally {
      release.countDown()
    }
  }
}

private[client] class ChildLoadedAkkaHttpClient {
  private val system = ActorSystem("child-loaded-http-client")
  private implicit val actorMaterializer: ActorMaterializer =
    ActorMaterializer.create(system)

  def singleRequest(uri: String): Future[AnyRef] =
    Http
      .get(system.asInstanceOf[ActorSystem])
      .singleRequest(HttpRequest(uri = uri))
      .asInstanceOf[Future[AnyRef]]

  def status(response: AnyRef): Int =
    response.asInstanceOf[HttpResponse].status.intValue()

  def discardEntity(response: AnyRef): Unit =
    response
      .asInstanceOf[HttpResponse]
      .discardEntityBytes(actorMaterializer)

  def terminate(): Unit =
    Await.result(system.terminate(), 10.seconds)
}
