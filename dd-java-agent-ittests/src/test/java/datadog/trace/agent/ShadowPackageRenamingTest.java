package datadog.trace.agent;

import com.google.common.collect.MapMaker;
import org.junit.Assert;
import org.junit.Test;

public class ShadowPackageRenamingTest {
  @Test
  public void agentDependenciesRenamed() throws Exception {
    final Class<?> ddClass =
        ClassLoader.getSystemClassLoader().loadClass("datadog.trace.agent.TracingAgent");

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
        "TracingAgent should reside in the -javaagent jar: " + agentSource,
        agentSource.matches(".*/dd-java-agent[^/]*.jar"));
    Assert.assertEquals("DD guava dep must be loaded from agent jar.", agentSource, agentGuavaDep);
    Assert.assertNotEquals(
        "User guava dep must not be loaded from agent jar.", agentSource, userGuava);
  }
}
