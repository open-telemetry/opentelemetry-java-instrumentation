package com.datadoghq.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import com.datadoghq.trace.sampling.AllSampler;

public class AllSamplerTest {


    @Test
    public void testAllSampler() {

    	 

         AllSampler sampler = new AllSampler();
         sampler.addSkipTagPattern("http.url", Pattern.compile(".*/hello"));
         
         DDSpan mockSpan = mock(DDSpan.class);
         Map<String,Object> tags = new HashMap<String,Object>();
         tags.put("http.url", "http://a/hello");
         when(mockSpan.getTags()).thenReturn(tags).thenReturn(tags);

         assertThat(sampler.sample(mockSpan)).isEqualTo(false);
         
         tags.put("http.url", "http://a/hello2");
         
         assertThat(sampler.sample(mockSpan)).isEqualTo(true);

    }
}