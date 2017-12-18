package dd.test;

import com.datadoghq.agent.TracingAgent;

public class TestUtils2 {

  public static void canSeeAgent() {
    System.out.println("Can I see the agent? " + TracingAgent.getAgentClassLoader());
  }
}
