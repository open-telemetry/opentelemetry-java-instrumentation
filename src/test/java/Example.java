import com.datadoghq.trace.Writer;
import com.datadoghq.trace.impl.Tracer;
import com.datadoghq.trace.writer.impl.DDAgentWriter;
import io.opentracing.Span;

public class Example {

    public static void main(String[] args) {


        Tracer tracer = new Tracer();
        Writer writer = new DDAgentWriter();

        Span parent = tracer
                .buildSpan("hello-world")
                .withServiceName("service-name")
                .start();

        parent.setBaggageItem("a-baggage", "value");
        parent.finish();

        Tracer.SpanBuilder builder =  (Tracer.SpanBuilder) tracer
                .buildSpan("hello-world")
                .asChildOf(parent);

        Span child = builder
                .withServiceName("service-name")
                .start();

        child.finish();

        writer.write(parent);
        writer.write(child);

        writer.close();

    }
}