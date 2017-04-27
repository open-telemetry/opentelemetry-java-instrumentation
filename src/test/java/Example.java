import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.Writer;
import com.datadoghq.trace.impl.DDTracer;
import com.datadoghq.trace.writer.impl.DDAgentWriter;

import io.opentracing.Span;

public class Example {

    public static void main(String[] args) throws Exception {


        Writer writer = new DDAgentWriter();
        DDTracer tracer = new DDTracer(writer, null);


        Span parent = tracer
                .buildSpan("hello-world")
                .withServiceName("service-name")
                .withSpanType("web")
                .start();

        parent.setBaggageItem("a-baggage", "value");


        Span child = tracer
                .buildSpan("hello-world")
                .asChildOf(parent)
                .withResourceName("resource-name")
                .start();

        child.finish();

        parent.finish();


        writer.close();

    }
}