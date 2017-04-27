package com.datadoghq.trace;

import io.opentracing.Span;

/**
 * Main interface to serialize/deserialize spans or collection of spans.
 */
public interface SpanSerializer {
	
	/**
	 * Serialize a single span
	 * 
	 * @param the span to serialize
	 * @return the serialized object
	 * @throws Exception
	 */
	public String serialize(Span span) throws Exception;
	
	/**
	 * A collection of Span to serialize
	 * 
	 * @param spans List or List of list of Spans
	 * @return the serialized objects
	 * @throws Exception
	 */
	public String serialize(Object spans) throws Exception;
	
	/**
	 * Deserialize a string to convert it in a Span or a Trace
	 * 
	 * @param str the string to deserialize
	 * @return A Span or a Trace (List<Span>)
	 * @throws Exception
	 */
	public Object deserialize(String str) throws Exception;
	
}
