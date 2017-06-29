package com.datadoghq.trace.agent;

import com.datadoghq.trace.Trace;

public class SayTracedHello {

	@Trace(operationName="SAY_HELLO",tagsKV={"service-name","test"})
	public static String sayHello(){
		return "hello!";
	}
	
	@Trace(operationName="SAY_HA",tagsKV={"service-name","test","span-type","DB"})
	public static String sayHA(){
		return "HA!!";
	}
	
	@Trace(operationName="NEW_TRACE",tagsKV={"service-name","test2"})
	public static String sayHELLOsayHA(){
		return sayHello()+sayHA();
	}
	
}
