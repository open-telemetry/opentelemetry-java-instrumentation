package datadog.trace.instrumentation.akkahttp

import java.util.Collections

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import datadog.trace.api.{DDSpanTypes, DDTags}
import io.opentracing.Span
import io.opentracing.log.Fields.ERROR_OBJECT
import io.opentracing.propagation.Format
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

import scala.util.{Failure, Success, Try}

object AkkaHttpClientTransformFlow {
  def transform[T](flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed]): Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    var span: Span = null

    Flow.fromFunction((input: (HttpRequest, T)) => {
      val (request, data) = input
      val scope = GlobalTracer.get
        .buildSpan("akka-http.request")
        .withTag(Tags.SPAN_KIND.getKey, Tags.SPAN_KIND_CLIENT)
        .withTag(Tags.HTTP_METHOD.getKey, request.method.value)
        .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
        .withTag(Tags.COMPONENT.getKey, "akka-http-client")
        .withTag(Tags.HTTP_URL.getKey, request.getUri.toString)
        .startActive(false)
      val headers = new AkkaHttpClientInstrumentation.AkkaHttpHeaders(request)
      GlobalTracer.get.inject(scope.span.context, Format.Builtin.HTTP_HEADERS, headers)
      span = scope.span
      scope.close()
      (headers.getRequest, data)
    }).via(flow).map(output => {
      output._1 match {
        case Success(response) => Tags.HTTP_STATUS.set(span, response.status.intValue)
        case Failure(e) =>
          Tags.ERROR.set(span, true)
          span.log(Collections.singletonMap(ERROR_OBJECT, e))
      }
      span.finish()
      output
    })
  }
}
