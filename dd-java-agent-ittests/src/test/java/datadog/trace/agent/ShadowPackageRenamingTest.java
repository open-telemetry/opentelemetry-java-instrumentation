package datadog.trace.agent;

import com.google.common.collect.MapMaker;
import datadog.trace.agent.test.IntegrationTestUtils;
import org.junit.Assert;
import org.junit.Test;

// TODO: move to spock
// TODO: merge with log rewrite test
public class ShadowPackageRenamingTest {

  @Test
  public void agentDependenciesRenamed() throws Exception {
    final Class<?> ddClass =
        IntegrationTestUtils.getAgentClassLoader()
            .loadClass("datadog.trace.agent.tooling.AgentInstaller");

    final String userGuava =
        MapMaker.class.getProtectionDomain().getCodeSource().getLocation().getFile();
    final String agentGuavaDep =
        ddClass
            .getClassLoader()
            .loadClass("datadog.trace.agent.deps.google.common.collect.MapMaker")
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .getFile();
    final String agentSource =
        ddClass.getProtectionDomain().getCodeSource().getLocation().getFile();

    Assert.assertTrue(
        "AgentInstaller should reside in the tmp tooling jar: " + agentSource,
        agentSource.matches(".*/agent-tooling-and-instrumentation[^/]*.jar"));
    Assert.assertEquals("DD guava dep must be loaded from agent jar.", agentSource, agentGuavaDep);
    Assert.assertNotEquals(
        "User guava dep must not be loaded from agent jar.", agentSource, userGuava);
  }

  // TODO: Write test
  // for every class in bootstrap jar:
  //   assert class not present in agent jar

  // TODO: Write test: assert agent classes not visible
}
