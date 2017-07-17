import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.writer.LoggingWriter;
import io.opentracing.Span;

public class ExampleWithLoggingWriter {

  public static void main(String[] args) throws Exception {

    DDTracer tracer = new DDTracer(new LoggingWriter(), new AllSampler());

    Span parent =
        tracer
            .buildSpan("hello-world")
            .withServiceName("service-name")
            .withSpanType("web")
            .startManual();

    parent.setBaggageItem("a-baggage", "value");

    Thread.sleep(100);

    Span child =
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
