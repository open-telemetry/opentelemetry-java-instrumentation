package datadog.trace.instrumentation.akkahttp;

import akka.NotUsed;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.japi.function.Function;
import akka.stream.scaladsl.Flow;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.atomic.AtomicReference;
import scala.Tuple2;
import scala.util.Try;

public class AkkaHttpClientTransformFlow {

  public static <T> Flow<Tuple2<HttpRequest, T>, Tuple2<Try<HttpResponse>, T>, NotUsed> transform(
      Flow<Tuple2<HttpRequest, T>, Tuple2<Try<HttpResponse>, T>, NotUsed> flow) {

    final AtomicReference<Span> spanRef = new AtomicReference<>(null);

    return akka.stream.javadsl.Flow.fromFunction(
            new Function<Tuple2<HttpRequest, T>, Tuple2<HttpRequest, T>>() {
              @Override
              public Tuple2<HttpRequest, T> apply(Tuple2<HttpRequest, T> param) throws Exception {
                HttpRequest request = param._1;
                T data = param._2;

                Span span = GlobalTracer.get().buildSpan("akka-http.request").start();
                spanRef.set(span);

                AkkaHttpClientDecorator.DECORATE.afterStart(span);
                AkkaHttpClientDecorator.DECORATE.onRequest(span, request);

                AkkaHttpClientInstrumentation.AkkaHttpHeaders headers =
                    new AkkaHttpClientInstrumentation.AkkaHttpHeaders(request);
                GlobalTracer.get().inject(span.context(), Format.Builtin.HTTP_HEADERS, headers);

                return new Tuple2<>(headers.getRequest(), data);
              }
            })
        .via(flow)
        .map(
            new Function<Tuple2<Try<HttpResponse>, T>, Tuple2<Try<HttpResponse>, T>>() {
              @Override
              public Tuple2<Try<HttpResponse>, T> apply(Tuple2<Try<HttpResponse>, T> param)
                  throws Exception {
                Span span = spanRef.get();
                try {
                  AkkaHttpClientDecorator.DECORATE.onResponse(span, param._1.get());
                } catch (Throwable t) {
                  AkkaHttpClientDecorator.DECORATE.onError(span, t);
                }
                AkkaHttpClientDecorator.DECORATE.beforeFinish(span);
                span.finish();
                return param;
              }
            })
        .asScala();
  }
}
