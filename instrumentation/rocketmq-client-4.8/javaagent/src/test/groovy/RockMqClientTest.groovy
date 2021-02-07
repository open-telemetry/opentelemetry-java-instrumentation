import io.opentelemetery.instrumentation.rocketmq.AbstractRocketMqClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait


/**
 * 1.rocketmq broker not supported embedded mode or testcontainers
 * 2.startup rocketmq according to quick-start, verify the namesvr and broker startup correctly, Note: DON'T do "Shutdown Servers" step. http://rocketmq.apache.org/docs/quick-start/
 */
class RockMqClientTest extends AbstractRocketMqClientTest implements AgentTestTrait {


}