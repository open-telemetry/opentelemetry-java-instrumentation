package com.datadoghq.trace;

import io.opentracing.Span;

public interface SpanSerializer {
	
	public String serialize(Span t) throws Exception;
	
	public String serialize(Object spans) throws Exception;
	
	public Span deserialize(String str) throws Exception;
	
}
