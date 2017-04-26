package com.datadoghq.trace.impl;

import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.SpanSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentracing.Span;

public class DDSpanSerializer implements SpanSerializer {

	protected final ObjectMapper objectMapper = new ObjectMapper();
	
	public String serialize(Span span) throws JsonProcessingException {
		return objectMapper.writeValueAsString(span);
	}
	
	public String serialize(Object spans) throws JsonProcessingException {
		return objectMapper.writeValueAsString(spans);
	}

	public io.opentracing.Span deserialize(String str) throws Exception {
		throw new UnsupportedOperationException("Deserialisation of spans is not implemented yet");
	}

	public static void main(String[] args) throws Exception{
		

		List<Span> array = new ArrayList<Span>();
		Tracer tracer = new Tracer();

		Span parent = tracer
				.buildSpan("hello-world")
				.withServiceName("service-name")
				.start();
		array.add(parent);

		parent.setBaggageItem("a-baggage", "value");

		Thread.sleep(1000);

		Span child = tracer
				.buildSpan("hello-world")
				.asChildOf(parent)
				.start();
		array.add(child);

		Thread.sleep(1000);

		child.finish();

		Thread.sleep(1000);

		parent.finish();

		List<List<Span>> traces = new ArrayList<List<Span>>();
		traces.add(array);
		
		DDSpanSerializer serializer = new DDSpanSerializer();
		
		System.out.println(serializer.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(traces));
		
	}
}
