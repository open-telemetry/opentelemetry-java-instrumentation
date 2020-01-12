package io.opentelemetry.smoketest


import io.opentelemetry.auto.agent.test.utils.OkHttpUtils
import io.opentelemetry.auto.agent.test.utils.PortUtils
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
