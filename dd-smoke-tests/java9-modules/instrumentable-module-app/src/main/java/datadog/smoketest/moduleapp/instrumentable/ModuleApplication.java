package datadog.smoketest.moduleapp.instrumentable;

public class ModuleApplication {
  public static void main(final String[] args) {
    final String agentStatus = System.getProperty("dd.agent.status");

    if (!"INSTALLED".equals(agentStatus)) {
      throw new RuntimeException("Incorrect agent status: " + agentStatus);
    }
  }
}
