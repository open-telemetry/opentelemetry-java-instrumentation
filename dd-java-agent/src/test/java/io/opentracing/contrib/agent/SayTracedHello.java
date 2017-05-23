package io.opentracing.contrib.agent;

public class SayTracedHello {

	@Trace
	public String sayHello(){
		return "hello!";
	}
	
}
