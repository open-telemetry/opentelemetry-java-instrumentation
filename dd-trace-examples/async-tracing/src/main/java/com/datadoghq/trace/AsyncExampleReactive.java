package com.datadoghq.trace;

import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.DDApi;
import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import io.reactivex.Observable;

public class AsyncExampleReactive {

  public static Integer bar() throws Exception {
    try (ActiveSpan __ = GlobalTracer.get().buildSpan("bar").startActive()) {
      System.out.println("bar");
      Thread.sleep(1000);
    }

    return 42;
  }

  public static Observable<Integer> foo() throws Exception {
    try (ActiveSpan span = GlobalTracer.get().buildSpan("foo").startActive()) {

      System.out.println("foo");
      Thread.sleep(500);

      final ActiveSpan.Continuation cont = span.capture();
      return Observable.fromCallable(
          () -> {
            try (ActiveSpan __ = cont.activate()) {
              return bar();
            }
          });
    }
  }

  public static void main(String[] args) throws Exception {
    DDAgentWriter writer = new DDAgentWriter(new DDApi("localhost", 8126));
    Tracer tracer = new DDTracer("dd-trace-test-app", writer, new AllSampler());
    GlobalTracer.register(tracer);

    foo().subscribe(System.out::println);

    writer.close();
  }
}
