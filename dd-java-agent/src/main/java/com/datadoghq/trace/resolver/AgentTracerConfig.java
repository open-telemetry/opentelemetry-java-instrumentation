package com.datadoghq.trace.resolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration POJO for the agent
 */
public class AgentTracerConfig extends TracerConfig {

	private boolean enableCustomTracing = false;
	
	private List<String> disabledInstrumentations = new ArrayList<String>();

	public List<String> getDisabledInstrumentations() {
		return disabledInstrumentations;
	}

	public void setDisabledInstrumentations(List<String> uninstallContributions) {
		this.disabledInstrumentations = uninstallContributions;
	}

	public boolean isEnableCustomTracing() {
		return enableCustomTracing;
	}

	public void setEnableCustomTracing(boolean enableCustomTracing) {
		this.enableCustomTracing = enableCustomTracing;
	}
	
}
