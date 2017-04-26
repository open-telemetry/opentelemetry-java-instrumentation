package com.datadoghq.trace.impl;

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
		throw new Exception("Deserialisation of spans is not implemented yet");
	}
}
