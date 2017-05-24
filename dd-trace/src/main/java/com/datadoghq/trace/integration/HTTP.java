package com.datadoghq.trace.integration;

import java.net.MalformedURLException;
import java.net.URL;

import com.datadoghq.trace.DDSpanContext;

import io.opentracing.tag.Tags;


/**
 * This span decorator leverages HTTP tags. It allows the dev to define a custom
 * service name  and retrieves some HTTP meta such as the request path
 */
public class HTTP implements DDSpanContextDecorator {

	protected final String componentName;
	protected final String desiredServiceName;
	
	public HTTP(String componentName) {
		super();
		this.componentName = componentName;
		this.desiredServiceName = null;
	}
	
	public HTTP(String componentName,String desiredServiceName) {
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
			}
			
			//Assign span type to WEB
			context.setSpanType("web");
		}
		
		//Assign resource name
		if(tag.equals(Tags.HTTP_URL.getKey())){
			try {
				String path = new URL(String.valueOf(value)).getPath();
				context.setResourceName(path);
			} catch (MalformedURLException e) {
				context.setResourceName(String.valueOf(value));
			}
		}
	}
	
	public String getComponentName() {
		return componentName;
	}

	public String getDesiredServiceName() {
		return desiredServiceName;
	}
}
