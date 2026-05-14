plugins {
  id("otel.java-conventions")
}

val apacheDubboVersion = "2.7.5"

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  api("org.apache.dubbo:dubbo:$apacheDubboVersion")
  api("org.apache.dubbo:dubbo-config-api:$apacheDubboVersion")

  implementation("io.opentelemetry:opentelemetry-api")
}
