plugins {
  id("otel.java-conventions")
}

val apacheDubboVersion = "2.7.5"

dependencies {
  api(project(":testing-common"))

  api("org.apache.dubbo:dubbo:$apacheDubboVersion")
  api("org.apache.dubbo:dubbo-config-api:$apacheDubboVersion")

  implementation("javax.annotation:javax.annotation-api:1.3.2")
  implementation("com.google.guava:guava")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
