package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;

import io.opentracing.tag.Tags;

public class DBServiceDecorator implements DDSpanContextDecorator {

	protected final String componentName;
	protected final String desiredServiceName;
	
	public DBServiceDecorator(String componentName) {
		super();
		this.componentName = componentName;
		this.desiredServiceName = null;
	}
	
	public DBServiceDecorator(String componentName,String desiredServiceName) {
		super();
		this.componentName = componentName;
		this.desiredServiceName = desiredServiceName;
	}

	@Override
	public void afterSetTag(DDSpanContext context, String tag, Object value) {
		//Assign service name
		if(tag.equals(Tags.COMPONENT.getKey()) && value.equals(componentName)){
			if(desiredServiceName != null){
				context.setServiceName(desiredServiceName);
			}else{
				context.setServiceName(componentName);
			}
			
			//Assign span type to DB
			context.setSpanType("db");
		}
		
		//Assign resource name
		if(tag.equals(Tags.DB_STATEMENT.getKey())){
			context.setResourceName(String.valueOf(value));
		}
	}
	
	public String getComponentName() {
		return componentName;
	}

	public String getDesiredServiceName() {
		return desiredServiceName;
	}
}
