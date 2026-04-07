plugins {
  id("otel.java-conventions")
}

val apacheDubboVersion = "2.7.5"

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  api("org.apache.dubbo:dubbo:$apacheDubboVersion")
  api("org.apache.dubbo:dubbo-config-api:$apacheDubboVersion")

  api("org.apache.dubbo:dubbo-registry-zookeeper:$apacheDubboVersion")
  api("org.apache.curator:curator-test:5.9.0")
  api("org.apache.curator:curator-recipes:5.9.0")

  implementation("javax.annotation:javax.annotation-api:1.3.2")

  implementation("io.opentelemetry:opentelemetry-api")
}
