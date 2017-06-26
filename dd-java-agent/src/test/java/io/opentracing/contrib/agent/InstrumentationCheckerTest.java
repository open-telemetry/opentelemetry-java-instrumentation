package io.opentracing.contrib.agent;

import com.datadoghq.trace.resolver.FactoryUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class InstrumentationCheckerTest {


	@Before
	public void setup() {
		Map<String, List<Map<String, String>>> rules = FactoryUtils.loadConfigFromResource("supported-version-test.yaml", Map.class);
		Map<String, String> frameworks = new HashMap<String, String>() {{
			put("artifact-1", "1.2.3.1232");
			put("artifact-2", "4.y.z");
			put("artifact-3", "5.123-1");
		}};

		new InstrumentationChecker(rules, frameworks);
	}


	@Test
	public void testRules() throws Exception {


		List<String> rules = InstrumentationChecker.getUnsupportedRules();
		assertThat(rules.size()).isEqualTo(3);
		assertThat(rules).containsExactlyInAnyOrder("unsupportedRuleOne", "unsupportedRuleTwo", "unsupportedRuleThree");


	}

}