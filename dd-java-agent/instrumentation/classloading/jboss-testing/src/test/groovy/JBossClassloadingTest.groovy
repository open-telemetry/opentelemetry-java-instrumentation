import datadog.trace.agent.test.AgentTestRunner
import org.jboss.modules.ModuleFinder
import org.jboss.modules.ModuleIdentifier
import org.jboss.modules.ModuleLoadException
import org.jboss.modules.ModuleLoader
import org.jboss.modules.ModuleSpec

class JBossClassloadingTest extends AgentTestRunner {
  def "delegates to bootstrap class loader for agent classes"() {
    setup:
    def moduleFinders = new ModuleFinder[1]
    moduleFinders[0] = new ModuleFinder() {
      @Override
      ModuleSpec findModule(ModuleIdentifier identifier, ModuleLoader delegateLoader) throws ModuleLoadException {
        return ModuleSpec.build(identifier).create()
      }
    }
    def moduleLoader = new ModuleLoader(moduleFinders)
    def moduleId = ModuleIdentifier.fromString("test")
    def testModule = moduleLoader.loadModule(moduleId)
    def classLoader = testModule.getClassLoader()

    when:
    Class<?> clazz
    try {
      clazz = Class.forName("datadog.trace.api.GlobalTracer", false, classLoader)
    } catch (ClassNotFoundException e) {
    }

    then:
    assert clazz != null
    assert clazz.getClassLoader() == null
  }
}
