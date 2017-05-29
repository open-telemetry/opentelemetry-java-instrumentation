package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;

/**
 * Span decorators are called when new tags are written and proceed to various remappings and enrichments
 */
public abstract class DDSpanContextDecorator {

	private String matchingTag;

	private String matchingValue;

	private String setTag;

	private String setValue;

	public boolean afterSetTag(DDSpanContext context, String tag, Object value){
		if(tag.equals(this.getMatchingTag()) && (this.getMatchingValue()==null || value.equals(this.getMatchingValue()))){
			String targetTag = getSetTag()==null?tag:getSetTag();
			String targetValue = getSetValue()==null?String.valueOf(value):getSetTag();
			context.setTag(targetTag, targetValue);
			return true;
		}else{
			return false;
		}
	}

	public String getMatchingTag() {
		return matchingTag;
	}

	public void setMatchingTag(String tag) {
		this.matchingTag = tag;
	}

	public String getMatchingValue() {
		return matchingValue;
	}

	public void setMatchingValue(String value) {
		this.matchingValue = value;
	}

	public String getSetTag() {
		return setTag;
	}

	public void setSetTag(String targetTag) {
		this.setTag = targetTag;
	}

	public String getSetValue() {
		return setValue;
	}

	public void setSetValue(String targetValue) {
		this.setValue = targetValue;
	}
}