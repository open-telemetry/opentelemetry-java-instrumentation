package com.datadoghq.trace.writer.impl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.datadoghq.trace.SpanSerializer;
import com.datadoghq.trace.impl.DDSpanSerializer;
import com.datadoghq.trace.impl.DDTags;
import com.datadoghq.trace.impl.Tracer;

import io.opentracing.Span;

public class DDApi {

	protected static final String TRACES_ENDPOINT = "/v0.3/traces";
	protected static final String TRACES_SERVICES = "/v0.3/services";
	
	protected final String host;
	protected final int port;
	protected final String tracesEndpoint;
	protected final String servicesEndpoint;
	protected final SpanSerializer spanSerializer;
	
	public DDApi(String host, int port) {
		this(host,port,null);
	}
	
	public DDApi(String host, int port,Optional<SpanSerializer> serializer) {
		super();
		this.host = host;
		this.port = port;
		this.tracesEndpoint = "http://"+host+":"+port+TRACES_ENDPOINT;
		this.servicesEndpoint = "http://"+host+":"+port+TRACES_SERVICES;
		this.spanSerializer = serializer.orElse(new DDSpanSerializer());
	}
	
	public void sendTraces(List<List<Span>> traces){
		
	}
	
	public void sendServices(List<String> services){
		
	}
	
	private int callPUT(String endpoint,String content){
		HttpURLConnection httpCon = null;
		try {
			URL url = new URL(tracesEndpoint);
			httpCon = (HttpURLConnection) url.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestMethod("PUT");
			httpCon.setRequestProperty("Content-Type", "application/json");
			OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
			out.write(content);
			out.close();
			return httpCon.getResponseCode();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		} 
	}
	
public static void main(String[] args) throws Exception{
		
		List<Span> array = new ArrayList<Span>();
        Tracer tracer = new Tracer();
       
        Span parent = tracer
                .buildSpan("hello-world")
                .withTag(DDTags.SERVICE.getKey(), "service-name")
                .start();
        array.add(parent);
        
        parent.setBaggageItem("a-baggage", "value");
        
        Thread.sleep(1000);

        Span child = tracer
                .buildSpan("hello-world")
                .asChildOf(parent)
                .start();
        array.add(child);
        
        Thread.sleep(1000);
        
        child.finish();
        
        Thread.sleep(1000);
        
        parent.finish();
		
		DDSpanSerializer ddSpanSerializer = new DDSpanSerializer();
		
		String str = ddSpanSerializer.serialize(array);
		str = "["+str+"]";
		
		DDApi api = new DDApi(DDAgentWriter.DEFAULT_HOSTNAME, DDAgentWriter.DEFAULT_PORT);
		int status = api.callPUT(api.tracesEndpoint, str);
		System.out.println("Status: "+status);
		
	}
}
