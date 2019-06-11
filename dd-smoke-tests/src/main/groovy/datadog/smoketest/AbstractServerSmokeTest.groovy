package datadog.smoketest


import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import okhttp3.OkHttpClient
import spock.lang.Shared

import java.util.concurrent.TimeUnit

abstract class AbstractServerSmokeTest extends AbstractSmokeTest {

  @Shared
  int httpPort = PortUtils.randomOpenPort()


  protected OkHttpClient client = OkHttpUtils.client()

  def setupSpec() {
    PortUtils.waitForPortToOpen(httpPort, 240, TimeUnit.SECONDS, serverProcess)
  }

}
