package datadog.trace.common.writer.ddagent;

import java.util.Map;

public interface DDAgentResponseListener {
  /** Invoked after the api receives a response from the core agent. */
  void onResponse(String endpoint, Map<String, Map<String, Number>> responseJson);
}
