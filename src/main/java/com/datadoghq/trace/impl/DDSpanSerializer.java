package com.datadoghq.trace.impl;

import java.io.IOException;

import com.datadoghq.trace.SpanSerializer;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DDSpanSerializer implements SpanSerializer {

	protected final ObjectMapper objectMapper = new ObjectMapper();
	
	public String serialize(io.opentracing.Span t) throws JsonProcessingException {
		return objectMapper.writeValueAsString(t);
	}

	public io.opentracing.Span deserialize(String str) throws JsonParseException, JsonMappingException, IOException {
		return objectMapper.readValue(str, DDSpan.class);
	}

	public static void main(String[] args) throws Exception{
		
		Tracer tracer = new Tracer();
		io.opentracing.Span span = tracer.buildSpan("Hello!")
				.withTag("port", 1234)
				.withTag("bool", true)
				.withTag("hello", "world")
				.start();
		DDSpanSerializer ddSpanSerializer = new DDSpanSerializer();
		
		System.out.println(ddSpanSerializer.serialize(span));
		
	}
}
