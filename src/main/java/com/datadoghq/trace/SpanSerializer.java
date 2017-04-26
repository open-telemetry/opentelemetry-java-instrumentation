package com.datadoghq.trace;

import java.util.List;

import io.opentracing.Span;

public interface SpanSerializer {
	
	public String serialize(Span t) throws Exception;
	
	public String serialize(List<Span> t) throws Exception;
	
	public Span deserialize(String str) throws Exception;
	
}
