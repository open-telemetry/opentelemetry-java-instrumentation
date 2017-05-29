package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import io.opentracing.tag.Tags;

/**
 * This span decorator leverages DB tags. It allows the dev to define a custom
 * service name  and retrieves some DB meta such as the statement
 */
public class DBComponent extends DDSpanContextDecorator {

	public DBComponent() {
		super();
		this.setMatchingTag(Tags.COMPONENT.getKey());
		this.setSetTag(DDTags.SERVICE_NAME);
	}

	@Override
	public boolean afterSetTag(DDSpanContext context, String tag, Object value) {
		//Assign service name
		if (super.afterSetTag(context, tag, value)) {
			//Assign span type to DB
			context.setSpanType("db");
			return true;
		}
		return false;
	}
}
