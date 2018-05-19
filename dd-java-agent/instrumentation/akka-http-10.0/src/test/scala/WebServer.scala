import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.Trace
import datadog.trace.instrumentation.akkahttp.AkkaHttpInstrumentation.{DatadogGraph, DatadogLogic}

import scala.concurrent.{Await, Future}

object WebServer {
  val port = TestUtils.randomOpenPort()
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher
  val asyncHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(GET, Uri.Path("/test"), _, _, _) =>
      Future {
        tracedMethod()
        HttpResponse(entity = "Hello unit test.")
      }
    case HttpRequest(GET, Uri.Path("/throw-handler"), _, _, _) =>
      sys.error("Oh no handler")
    case HttpRequest(GET, Uri.Path("/throw-callback"), _, _, _) =>
      Future {
        sys.error("Oh no callback")
      }
    case HttpRequest(GET, Uri.Path("/server-error"), _, _, _) =>
      Future {
        HttpResponse(entity = "Error unit test.", status = StatusCodes.InternalServerError)
      }
    case _ =>
      Future {
        HttpResponse(entity = "Not found unit test.", status = StatusCodes.NotFound)
      }
  }

  private var binding :ServerBinding = null

  def start(): Unit = synchronized {
    if (null == binding) {
      import scala.concurrent.duration._
      binding = Await.result(Http().bindAndHandleAsync(asyncHandler, "localhost", port), 10 seconds)
    }
  }

  def stop(): Unit = synchronized {
    if (null != binding) {
      binding.unbind()
      system.terminate()
      binding = null
    }
  }

  @Trace
  def tracedMethod(): Unit = {
  }
}
