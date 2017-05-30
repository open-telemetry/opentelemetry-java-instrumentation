package com.datadoghq.trace.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class DDSpanDecoratorConfig {
	
	private String type;
	
	private String matchingTag;
	
	private String matchingValue;
	
	private String setTag;
	
	private String setValue;
	
	public String getMatchingTag() {
		return matchingTag;
	}

	public void setMatchingTag(String matchingTag) {
		this.matchingTag = matchingTag;
	}

	public String getMatchingValue() {
		return matchingValue;
	}

	public void setMatchingValue(String matchingValue) {
		this.matchingValue = matchingValue;
	}

	public String getSetTag() {
		return setTag;
	}

	public void setSetTag(String setTag) {
		this.setTag = setTag;
	}

	public String getSetValue() {
		return setValue;
	}

	public void setSetValue(String setValue) {
		this.setValue = setValue;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	@Override
	public String toString() {
		try {
			return new ObjectMapper(new YAMLFactory()).writeValueAsString(this);
		} catch (JsonProcessingException e) {
			return null;
		}
	}
	
}
