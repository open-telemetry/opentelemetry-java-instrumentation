package datadog.trace.instrumentation.akkahttp

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import datadog.trace.instrumentation.akkahttp.AkkaHttpClientDecorator.DECORATE
import io.opentracing.Span
import io.opentracing.propagation.Format
import io.opentracing.util.GlobalTracer

import scala.util.{Failure, Success, Try}

object AkkaHttpClientTransformFlow {
  def transform[T](flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed]): Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    var span: Span = null

    Flow.fromFunction((input: (HttpRequest, T)) => {
      val (request, data) = input
      span = GlobalTracer.get.buildSpan("akka-http.request").start()
      DECORATE.afterStart(span)
      DECORATE.onRequest(span, request)
      val headers = new AkkaHttpClientInstrumentation.AkkaHttpHeaders(request)
      GlobalTracer.get.inject(span.context, Format.Builtin.HTTP_HEADERS, headers)
      (headers.getRequest, data)
    }).via(flow).map(output => {
      output._1 match {
        case Success(response) => DECORATE.onResponse(span, response)
        case Failure(e) => DECORATE.onError(span, e)
      }
      DECORATE.beforeFinish(span)
      span.finish()
      output
    })
  }
}
