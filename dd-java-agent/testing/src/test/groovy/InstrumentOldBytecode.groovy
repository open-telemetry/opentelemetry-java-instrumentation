import com.ibm.as400.resource.ResourceLevel
import io.opentelemetry.auto.agent.test.AgentTestRunner

class InstrumentOldBytecode extends AgentTestRunner {
  def "can instrument old bytecode"() {
    expect:
    new ResourceLevel().toString() == "instrumented"
  }
}
