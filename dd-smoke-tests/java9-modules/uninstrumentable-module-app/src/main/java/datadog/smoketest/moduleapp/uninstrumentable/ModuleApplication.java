package datadog.smoketest.moduleapp.uninstrumentable;

public class ModuleApplication {
  public static void main(final String[] args) {
    final String agentStatus = System.getProperty("dd.agent.status");

    if (!"NOT_INSTALLED".equals(agentStatus)) {
      throw new RuntimeException("Incorrect agent status: " + agentStatus);
    }
  }
}
