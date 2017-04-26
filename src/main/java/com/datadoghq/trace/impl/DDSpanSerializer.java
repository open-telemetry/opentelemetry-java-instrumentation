package com.datadoghq.trace.impl;

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
	
	public String serialize(List<Span> spans) throws JsonProcessingException {
		return objectMapper.writeValueAsString(spans);
	}

	public io.opentracing.Span deserialize(String str) throws Exception {
		throw new Exception("Deserialisation of spans is not implemented yet");
	}

	public static void main(String[] args) throws Exception{
		
		Tracer tracer = new Tracer();
		Span span = tracer.buildSpan("Hello!")
				.withTag("port", 1234)
				.withTag("bool", true)
				.withTag("hello", "world")
				.start();
		DDSpanSerializer ddSpanSerializer = new DDSpanSerializer();
		
		System.out.println(ddSpanSerializer.serialize(span));
		
	}
}
