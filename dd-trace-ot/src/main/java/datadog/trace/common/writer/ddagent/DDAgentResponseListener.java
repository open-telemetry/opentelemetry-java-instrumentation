package datadog.trace.common.writer.ddagent;

import com.fasterxml.jackson.databind.JsonNode;

public interface DDAgentResponseListener {
  /** Invoked after the api receives a response from the core agent. */
  void onResponse(String endpoint, JsonNode responseJson);
}
