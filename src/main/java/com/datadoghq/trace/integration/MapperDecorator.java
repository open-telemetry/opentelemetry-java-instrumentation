package com.datadoghq.trace.integration;

import java.util.Map;

import com.datadoghq.trace.DDSpanContext;

/**
 * Remap some tags to other tags
 */
public class MapperDecorator implements DDSpanContextDecorator {

	public static final String SPAN_TYPE = "spanType";
	public static final String SERVICE_NAME = "serviceName";
	public static final String RESOURCE_NAME = "resourceName";
	
	private final Map<String,String> mappings;
	
	public MapperDecorator(Map<String, String> mappings) {
		super();
		this.mappings = mappings;
	}

	@Override
	public void afterSetTag(DDSpanContext context, String tag, Object value) {
		String toAssign = mappings.get(tag);
		if(toAssign != null){
			if(toAssign.equals(SPAN_TYPE)){
				context.setSpanType(String.valueOf(value));
			}else if(toAssign.equals(SERVICE_NAME)){
				context.setServiceName(String.valueOf(value));
			}else if(toAssign.equals(RESOURCE_NAME)){
				context.setResourceName(String.valueOf(value));
			}else{
				//General remap
				context.setTag(toAssign, value);
			}
		}
	}
	
}
