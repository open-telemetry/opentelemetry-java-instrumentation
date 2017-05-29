package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

import java.net.MalformedURLException;

public class URLAsResourceName extends DDSpanContextDecorator {

	public URLAsResourceName() {
		super();
		this.setMatchingTag(Tags.HTTP_URL.getKey());
		this.setSetTag(DDTags.RESOURCE_NAME);
	}

	@Override
	public boolean afterSetTag(DDSpanContext context, String tag, Object value) {
		//Assign resource name
		try {
			String path = new java.net.URL(String.valueOf(value)).getPath();
			context.setTag(this.getSetTag(), path);
		} catch (MalformedURLException e) {
			context.setResourceName(String.valueOf(value));
		}
		return true;
	}

}
