import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.Service;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.writer.LoggingWriter;
import io.opentracing.Span;

public class ExampleWithLoggingWriter {

  public static void main(final String[] args) throws Exception {

    final DDTracer tracer = new DDTracer(new LoggingWriter(), new AllSampler());
    tracer.addServiceInfo(new Service("service-foo", "mongo", Service.AppType.WEB));

    final Span parent =
        tracer
            .buildSpan("hello-world")
            .withServiceName("service-foo")
            .withSpanType("web")
            .startManual();

    parent.setBaggageItem("a-baggage", "value");

    Thread.sleep(100);

    final Span child =
        tracer
            .buildSpan("hello-world")
            .asChildOf(parent)
            .withResourceName("resource-name")
            .startManual();

    Thread.sleep(100);

    child.finish();

    Thread.sleep(100);

    parent.finish();
  }
}
