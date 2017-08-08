import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.Service;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.writer.LoggingWriter;
import io.opentracing.Span;

public class ExampleWithLoggingWriter {

  public static void main(final String[] args) throws Exception {

    final DDTracer tracer = new DDTracer(new LoggingWriter(), new AllSampler());
    tracer.addServiceInfo(new Service("api-intake", "spark", Service.AppType.CACHE));

    final Span parent =
      tracer.buildSpan("fetch.backend").withServiceName("api-intake").startManual();

    parent.setBaggageItem("scope-id", "a-1337");

    Thread.sleep(100);

    final Span child =
        tracer
          .buildSpan("delete.resource")
            .asChildOf(parent)
          .withResourceName("delete")
            .startManual();

    Thread.sleep(100);

    child.finish();

    Thread.sleep(100);

    parent.finish();
    tracer.close();
  }
}
