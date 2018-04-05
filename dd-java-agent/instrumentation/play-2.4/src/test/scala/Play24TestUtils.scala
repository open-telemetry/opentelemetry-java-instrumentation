import java.lang.reflect.Field

import play.api.mvc.Action
import play.api.routing.Router
import play.api.mvc._
import play.api.routing.sird._
import datadog.trace.api.Trace
import play.inject.DelegateInjector

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import play.api.inject.bind

import scala.concurrent.ExecutionContext.Implicits.global

object Play24TestUtils {
  def buildTestApp(): play.Application = {
    // build play.api.Application with desired setting and pass into play.Application for testing
    val apiApp :play.api.Application = new play.api.inject.guice.GuiceApplicationBuilder()
        .overrides(bind[Router].toInstance(Router.from {
          case GET(p"/helloplay/$from") => Action { req: RequestHeader =>
            HandlerSetter.setHandler(req, "/helloplay/:from")
            val f: Future[String] = Future[String] {
              TracedWork.doWork()
              from
            }
            Results.Ok(s"hello " + Await.result(f, 5 seconds))
          }
          case GET(p"/make-error") => Action { req: RequestHeader =>
            HandlerSetter.setHandler(req, "/make-error")
            Results.InternalServerError("Really sorry...")
          }
          case GET(p"/exception") => Action { req: RequestHeader =>
            HandlerSetter.setHandler(req, "/exception")
            if (System.currentTimeMillis() > 0) {
              throw new RuntimeException("oh no")
            }
            Results.Ok("hello")
          }
          case _ => Action {
            Results.NotFound("Sorry..")
          }
        }))
      .build()


    return new play.DefaultApplication(apiApp, new DelegateInjector(apiApp.injector))
  }
}

object TracedWork {
  @Trace
  def doWork(): Unit = {
  }
}

object HandlerSetter {
  def setHandler(req: RequestHeader, path: String): Unit = {
    val rh = getField(req, "rh$1")
    val newTags: Map[String, String] = Map(Router.Tags.RoutePattern -> path)
    val f: Field = rh.getClass().getDeclaredField("tags")
    f.setAccessible(true)
    f.set(rh, newTags)
    f.setAccessible(false)
  }

  private def getField(o: Object, fieldName :String): Object = {
    val f: Field = o.getClass().getDeclaredField(fieldName)
    f.setAccessible(true)
    val result: Object = f.get(o)
    f.setAccessible(false)
    return result
  }
}

class Play24TestUtils {}
