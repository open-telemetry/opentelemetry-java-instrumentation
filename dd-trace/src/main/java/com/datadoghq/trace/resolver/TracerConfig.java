package com.datadoghq.trace.resolver;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Tracer configuration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TracerConfig {
	private String defaultServiceName;
	private Map<String,Object> writer;
	private Map<String,Object> sampler;
	private List<DDSpanDecoratorConfig> decorators;
	
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
	public List<DDSpanDecoratorConfig> getDecorators() {
		return decorators;
	}
	public void setDecorators(List<DDSpanDecoratorConfig> decorators) {
		this.decorators = decorators;
	}
	
	@Override
	public String toString() {
		try {
			return new ObjectMapper(new YAMLFactory()).writeValueAsString(this);
		} catch (JsonProcessingException e) {
			//FIXME better toString() while config object stabilized
			return null;
		}
	}
}
