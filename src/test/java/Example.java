import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.Writer;
import com.datadoghq.trace.impl.DDTracer;
import com.datadoghq.trace.writer.impl.DDAgentWriter;

import io.opentracing.Span;

public class Example {

    public static void main(String[] args) throws Exception {


        DDTracer tracer = new DDTracer();


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



    }
}