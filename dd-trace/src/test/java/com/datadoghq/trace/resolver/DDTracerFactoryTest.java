package com.datadoghq.trace.resolver;

import com.datadoghq.trace.sampling.AllSampler;
import com.datadoghq.trace.sampling.RateSampler;
import com.datadoghq.trace.writer.DDAgentWriter;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class DDTracerFactoryTest {




	@Test
	public void test() throws Exception {
		TracerConfig tracerConfig = FactoryUtils.loadConfigFromResource("dd-trace-1.yaml", TracerConfig.class);

		assertThat(tracerConfig.getWriter()).isNotNull();
		assertThat(tracerConfig.getSampler()).isNotNull();
		assertThat(tracerConfig.getDefaultServiceName()).isEqualTo("java-app");
		assertThat(tracerConfig.getWriter().getHost()).isEqualTo("foo");
		assertThat(tracerConfig.getWriter().getPort()).isEqualTo(123);
		assertThat(tracerConfig.getWriter().getType()).isEqualTo(DDAgentWriter.class.getSimpleName());
		assertThat(tracerConfig.getSampler().getType()).isEqualTo(AllSampler.class.getSimpleName());
		assertThat(tracerConfig.getSampler().getRate()).isNull();


		tracerConfig = FactoryUtils.loadConfigFromResource("dd-trace-2.yaml", TracerConfig.class);
		assertThat(tracerConfig.getWriter()).isNotNull();
		assertThat(tracerConfig.getDefaultServiceName()).isEqualTo("java-app");
		assertThat(tracerConfig.getWriter().getHost("localhost")).isEqualTo("localhost");
		assertThat(tracerConfig.getWriter().getPort(8126)).isEqualTo(8126);
		assertThat(tracerConfig.getWriter().getType()).isEqualTo(DDAgentWriter.class.getSimpleName());
		assertThat(tracerConfig.getSampler().getType()).isEqualTo(RateSampler.class.getSimpleName());
		assertThat(tracerConfig.getSampler().getRate()).isEqualTo(0.4);



	}

}