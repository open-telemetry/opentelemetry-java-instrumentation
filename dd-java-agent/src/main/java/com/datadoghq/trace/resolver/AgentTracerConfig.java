package com.datadoghq.trace.resolver;

/**
 * Configuration POJO for the agent
 */
public class AgentTracerConfig extends TracerConfig {

	private boolean enableCustomTracing = false;

	public boolean isEnableCustomTracing() {
		return enableCustomTracing;
	}

	public void setEnableCustomTracing(boolean enableCustomTracing) {
		this.enableCustomTracing = enableCustomTracing;
	}
}
