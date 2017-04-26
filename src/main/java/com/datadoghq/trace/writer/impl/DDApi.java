package com.datadoghq.trace.writer.impl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

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
		try {
			URL url = new URL(tracesEndpoint);
			HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
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
}
