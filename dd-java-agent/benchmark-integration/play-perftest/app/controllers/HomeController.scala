package controllers

import datadog.trace.api.Trace
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import javax.inject.Inject

import play.api.mvc._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's work page which does busy wait to simulate some work
  */
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  /**
    * Create an Action to perform busy wait
    */
  def doGet(workTimeMS: Option[Long], error: Option[String]) = Action { implicit request: Request[AnyContent] =>
    error match {
      case Some(x) => throw new RuntimeException("some sync error")
      case None => {
        var workTime = workTimeMS.getOrElse(0l)
        scheduleWork(workTime)
        Ok("Did " + workTime + "ms of work.")
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
