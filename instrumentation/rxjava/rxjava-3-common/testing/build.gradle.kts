plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("io.reactivex.rxjava3:rxjava:3.0.12")

  implementation("io.opentelemetry:opentelemetry-extension-annotations")
  implementation("com.google.guava:guava")
  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
