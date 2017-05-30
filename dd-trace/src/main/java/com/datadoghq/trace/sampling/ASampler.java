package com.datadoghq.trace.sampling;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.datadoghq.trace.DDSpan;

import io.opentracing.Span;

public abstract class ASampler implements Sampler{

	/**
	 * Sample tags
	 */
	protected Map<String,Pattern> skipTagsPatterns = new HashMap<String,Pattern>();
	
	@Override
	public boolean sample(Span span) {
		if(span instanceof DDSpan){
			DDSpan ddSpan = (DDSpan) span;
			
			//Filter by tag values
			for(Entry<String, Pattern> entry: skipTagsPatterns.entrySet()){
				Object value = ddSpan.getTags().get(entry.getKey());
				if(value != null){
					String strValue = String.valueOf(value);
					Pattern skipPattern = entry.getValue();
					if(skipPattern.matcher(strValue).matches()){
						return false;
					}
				}
			}
		}
		
		return doSample(span);
	}
	
	/**
	 * Pattern based skipping of tag values
	 * 
	 * @param tag
	 * @param skipPattern
	 */
	public void addSkipTagPattern(String tag,Pattern skipPattern){
		skipTagsPatterns.put(tag, skipPattern);
	}
	
	protected abstract boolean doSample(Span span);

}
