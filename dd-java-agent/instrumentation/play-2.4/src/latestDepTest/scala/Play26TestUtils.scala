import java.lang.reflect.Field

import datadog.trace.api.Trace
import play.api.mvc.request.RequestAttrKey
import play.api.mvc.{Action, _}
import play.api.routing.sird._
import play.api.routing.{HandlerDef, Router}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Play26TestUtils {
  def buildTestApp(): play.Application = {
    // build play.api.Application with desired setting and pass into play.Application for testing
    val apiApp: play.api.Application = new play.api.inject.guice.GuiceApplicationBuilder()
      .requireAtInjectOnConstructors(true)
      .router(
        Router.from {
          case GET(p"/helloplay/$from") => Action { req: RequestHeader =>
            HandlerSetter.setHandler(req, "/helloplay/:from")
            // FIXME: Add WS request for testing.
            //          implicit val application = Play.current
            //          val wsRequest = WS.url("http://localhost:" + port).get()
            val f: Future[String] = Future[String] {
              TracedWork.doWork()
              from
            }(Action.executionContext)
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
        })
      .build()

    return new play.DefaultApplication(apiApp, new play.inject.guice.GuiceApplicationBuilder().build().injector())
  }
}

object TracedWork {
  @Trace
  def doWork(): Unit = {
  }
}

object HandlerSetter {
  def setHandler(req: RequestHeader, path: String): Unit = {
    val f: Field = req.getClass().getDeclaredField("attrs")
    f.setAccessible(true)
    f.set(req, req.attrs
      .updated(play.routing.Router.Attrs.HANDLER_DEF.underlying(), new HandlerDef(null, null, null, null, null, null, path, null, null))
      .updated(RequestAttrKey.Tags, Map(play.routing.Router.Tags.ROUTE_PATTERN -> path)))
    f.setAccessible(false)
  }
}

class Play26TestUtils {}
