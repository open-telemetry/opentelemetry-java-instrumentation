import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import com.datadoghq.trace.writer.LoggingWritter;
import com.datadoghq.trace.writer.Writer;

import io.opentracing.Span;

public class ExampleWithLoggingWriter {

    public static void main(String[] args) throws Exception {

        DDTracer tracer = new DDTracer(new LoggingWritter(), new AllSampler());

        Span parent = tracer
                .buildSpan("hello-world")
                .withServiceName("service-name")
                .withSpanType("web")
                .start();

        parent.setBaggageItem("a-baggage", "value");

        Thread.sleep(100);

        Span child = tracer
                .buildSpan("hello-world")
                .asChildOf(parent)
                .withResourceName("resource-name")
                .start();

        Thread.sleep(100);

        child.finish();

        Thread.sleep(100);

        parent.finish();

    }
}