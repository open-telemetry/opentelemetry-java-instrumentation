package com.datadoghq.trace.writer.impl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.impl.DDSpanSerializer;
import com.datadoghq.trace.impl.Tracer;

import io.opentracing.Span;

public class DDApi {

	protected static final String TRACES_ENDPOINT = "/v0.3/traces";
	protected static final String TRACES_SERVICES = "/v0.3/services";
	
	protected final String host;
	protected final int port;
	protected final String tracesEndpoint;
	protected final String servicesEndpoint;
	
	public DDApi(String host, int port) {
		super();
		this.host = host;
		this.port = port;
		this.tracesEndpoint = "http://"+host+":"+port+TRACES_ENDPOINT;
		this.servicesEndpoint = "http://"+host+":"+port+TRACES_SERVICES;
	}
	
	public void sendSpans(List<Span> spans){
		
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
			out.write("Resource content");
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
		
		Tracer tracer = new Tracer();
		List<Span> array = new ArrayList<Span>();
		Span span = tracer.buildSpan("Hello!")
//				.withTag("port", 1234)
//				.withTag("bool", true)
				.withTag("hello", "world")
				.start();
		array.add(span);
		
		Span span2 = tracer.buildSpan("Hello2!")
//				.withTag("port", 1234)
//				.withTag("bool", true)
				.withTag("hello", "world")
				.start();
		array.add(span2);
		
		DDSpanSerializer ddSpanSerializer = new DDSpanSerializer();
		
		
		
		String str = ddSpanSerializer.serialize(array);
		str = "["+str+"]";
		
		DDApi api = new DDApi(DDAgentWriter.DEFAULT_HOSTNAME, DDAgentWriter.DEFAULT_PORT);
		int status = api.callPUT(api.tracesEndpoint, str);
		System.out.println("Status: "+status);
		
	}
}
