package com.datadoghq.trace.resolver;

import java.util.List;
import java.util.Map;

/**
 * Tracer configuration
 */
public class TracerConfig {
	private String defaultServiceName;
	private Map<String,Object> writer;
	private Map<String,Object> sampler;
	private List<Map<String,Object>> decorators;
	
	public String getDefaultServiceName() {
		return defaultServiceName;
	}
	public void setDefaultServiceName(String defaultServiceName) {
		this.defaultServiceName = defaultServiceName;
	}
	public Map<String, Object> getWriter() {
		return writer;
	}
	public void setWriter(Map<String, Object> writer) {
		this.writer = writer;
	}
	public Map<String, Object> getSampler() {
		return sampler;
	}
	public void setSampler(Map<String, Object> sampler) {
		this.sampler = sampler;
	}
	public List<Map<String, Object>> getDecorators() {
		return decorators;
	}
	public void setDecorators(List<Map<String, Object>> decorators) {
		this.decorators = decorators;
	}
}
