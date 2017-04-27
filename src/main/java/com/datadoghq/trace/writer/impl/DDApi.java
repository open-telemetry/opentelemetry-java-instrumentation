package com.datadoghq.trace.writer.impl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.datadoghq.trace.SpanSerializer;
import com.datadoghq.trace.impl.DDSpan;
import com.datadoghq.trace.impl.DDSpanSerializer;
import com.datadoghq.trace.impl.DDTracer;

import io.opentracing.Span;

public class DDApi {

	protected static final String TRACES_ENDPOINT = "/v0.3/traces";
	protected static final String SERVICES_ENDPOINT = "/v0.3/services";

	protected final String host;
	protected final int port;
	protected final String tracesEndpoint;
	protected final String servicesEndpoint;
	protected final SpanSerializer spanSerializer;

	public DDApi(String host, int port) {
		this(host,port,new DDSpanSerializer());
	}

	public DDApi(String host, int port,SpanSerializer spanSerializer) {
		super();
		this.host = host;
		this.port = port;
		this.tracesEndpoint = "http://"+host+":"+port+TRACES_ENDPOINT;
		this.servicesEndpoint = "http://"+host+":"+port+SERVICES_ENDPOINT;
		this.spanSerializer = spanSerializer;
	}

	public boolean sendTraces(List<List<Span>> traces){
		try {
			String payload = spanSerializer.serialize(traces);
			int status = callPUT(tracesEndpoint,payload);
			if(status == 200){
				return true;
			}else{
				
				//FIXME log status here
				
				return false;
			}
		} catch (Exception e) {
			//FIXME proper exceptino
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean sendServices(List<String> services){
		return false;
	}

	private int callPUT(String endpoint,String content){
		HttpURLConnection httpCon = null;
		try {
			URL url = new URL(endpoint);
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
		DDTracer tracer = new DDTracer();

		Span parent = tracer
				.buildSpan("hello-world")
				.withServiceName("service-name")
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

		DDAgentWriter writer = new DDAgentWriter();
		writer.write(((DDSpan)parent.getTrace());

		Thread.sleep(1000);
		
		writer.close();

	}
}
