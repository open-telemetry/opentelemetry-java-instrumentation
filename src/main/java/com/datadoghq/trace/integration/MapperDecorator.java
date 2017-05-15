package com.datadoghq.trace.integration;

import java.util.Map;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;

/**
 * This is a generic decorator, it remaps some tags to other tags
 */
public class MapperDecorator implements DDSpanContextDecorator {
	
	private final Map<String,String> mappings;
	
	public MapperDecorator(Map<String, String> mappings) {
		super();
		this.mappings = mappings;
	}

	@Override
	public void afterSetTag(DDSpanContext context, String tag, Object value) {
		String toAssign = mappings.get(tag);
		if(toAssign != null){
			if(toAssign.equals(DDTags.SPAN_TYPE)){
				context.setSpanType(String.valueOf(value));
			}else if(toAssign.equals(DDTags.SERVICE_NAME)){
				context.setServiceName(String.valueOf(value));
			}else if(toAssign.equals(DDTags.RESOURCE_NAME)){
				context.setResourceName(String.valueOf(value));
			}else{
				//General remap
				context.setTag(toAssign, value);
			}
		}
	}
	
}
