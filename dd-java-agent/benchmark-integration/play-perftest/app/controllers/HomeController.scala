package controllers

import datadog.trace.api.Trace
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import javax.inject.Inject

import play.api.mvc._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
    * Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method
    * will be called when the application receives a `GET` request with
    * a path of `/`.
    */
  def doGet(workTimeMS: Option[Long], error: Option[String]) = Action { implicit request: Request[AnyContent] =>
    error match {
      case Some(x) => throw new RuntimeException("some sync error")
      case None => {
        //val workVal: String = request.queryString("workTimeMS").headOption.getOrElse("")
        var workTime = workTimeMS.getOrElse(0l)
        scheduleWork(workTime)
        Ok("DONE WORK IN " + workTime + " MS")
      }
    }

  }

  @Trace
  private def scheduleWork(workTimeMS: Long) {
    val span = GlobalTracer.get().activeSpan
    if (span != null) {
      span.setTag("work-time", workTimeMS)
      span.setTag("info", "interesting stuff")
      span.setTag("additionalInfo", "interesting stuff")
    }
    if (workTimeMS > 0) {
      Worker.doWork(workTimeMS)
    }
  }
}
