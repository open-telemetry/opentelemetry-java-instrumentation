import com.datadoghq.trace.Sampler;
import com.datadoghq.trace.Writer;
import com.datadoghq.trace.impl.AllSampler;
import com.datadoghq.trace.impl.DDTracer;
import com.datadoghq.trace.writer.impl.DDAgentWriter;
import io.opentracing.Span;

public class ExampleWithDDAgentWriter {

    public static void main(String[] args) throws Exception {

        // Instantiate the DDWriter
        // By default, traces are written to localhost:8126 (the ddagent)
        Writer writer = new DDAgentWriter();

        // Instantiate the proper Sampler
        // - RateSampler if you want to keep `ratio` traces
        // - AllSampler to keep all traces
        Sampler sampler = new AllSampler();


        // Create the tracer
        DDTracer tracer = new DDTracer(writer, sampler);


        Span parent = tracer
                .buildSpan("hello-world")
                .withServiceName("service-name")
                .withSpanType("web")
                .start();

        Thread.sleep(100);

        parent.setBaggageItem("a-baggage", "value");

        Span child = tracer
                .buildSpan("hello-world")
                .asChildOf(parent)
                .withResourceName("resource-name")
                .start();

        Thread.sleep(100);

        child.finish();

        Thread.sleep(100);

        parent.finish();

        writer.close();

    }
}