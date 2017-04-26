import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.Writer;
import com.datadoghq.trace.impl.Tracer;
import com.datadoghq.trace.writer.impl.DDAgentWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentracing.Span;

public class Example {

    public static void main(String[] args) throws Exception{
    	List<Span> trace = new ArrayList<Span>();
    	
    	Tracer tracer = new Tracer();
        Writer writer = new DDAgentWriter();

        Span parent = tracer
                .buildSpan("hello-world")
                .withServiceName("service-name")
                .withSpanType("web")
                .start();

        parent.setBaggageItem("a-baggage", "value");
        trace.add(parent);
        parent.finish();

        Span child = tracer
                .buildSpan("hello-world")
                .asChildOf(parent)
                .withResourceName("resource-name")
        		.start();
        child.finish();
        trace.add(child);
        parent.finish();
        
        writer.write(trace);
        
        writer.close();

    }
}