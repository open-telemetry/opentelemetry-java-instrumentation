plugins {
  id("otel.java-conventions")
}

val sofaRpcVersion = "5.4.0"

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  api("com.alipay.sofa:sofa-rpc-all:$sofaRpcVersion")

  implementation("javax.annotation:javax.annotation-api:1.3.2")
  implementation("com.google.guava:guava")

  implementation("io.opentelemetry:opentelemetry-api")
}


configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.13")
    force("ch.qos.logback:logback-core:1.2.13")
    force("org.slf4j:slf4j-api:1.7.21")
  }
}
