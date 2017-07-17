package com.datadoghq.trace;

import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.DDApi;
import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncExample {

  private static ExecutorService ex = Executors.newFixedThreadPool(1);

  public static Integer bar() throws Exception {

    try (ActiveSpan __ = GlobalTracer.get().buildSpan("bar").startActive()) {
      System.out.println("bar");
      Thread.sleep(1000);
    }

    return 42;
  }

  public static Future<Integer> foo() throws Exception {

    try (ActiveSpan span = GlobalTracer.get().buildSpan("foo").startActive()) {

      System.out.println("foo");
      Thread.sleep(500);

      final ActiveSpan.Continuation cont = span.capture();

      Future<Integer> future =
          ex.submit(
              () -> {
                try (ActiveSpan __ = cont.activate()) {

                  return bar();
                }
              });

      return future;
    }
  }

  public static void main(String[] args) throws Exception {

    DDAgentWriter writer = new DDAgentWriter(new DDApi("localhost", 8126));
    Tracer tracer = new DDTracer("dd-trace-test-app", writer, new AllSampler());
    //		Tracer tracer = new DDTracer(new LoggingWriter(), new AllSampler());
    GlobalTracer.register(tracer);

    System.out.printf("%d%n", foo().get());

    writer.close();
    ex.shutdownNow();
  }
}
