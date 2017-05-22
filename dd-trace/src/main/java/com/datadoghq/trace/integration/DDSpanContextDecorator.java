package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;

/**
 * Span decorators are called when new tags are written and proceed to various remappings and enrichments
 */
public interface DDSpanContextDecorator {

	/**
	 * A tag was just added to the context. The decorator provides a way to enrich the context a bit more.
	 * 
	 * @param context the target context to decorate
	 * @param tag The tag set
	 * @param value the value assigned to the tag
	 */
	public void afterSetTag(DDSpanContext context , String tag, Object value);
	
	
}