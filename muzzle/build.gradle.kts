plugins {
  id("otel.java-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  // Only used during compilation by bytebuddy plugin
  compileOnly("com.google.guava:guava")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  api("net.bytebuddy:byte-buddy")

  implementation(project(":javaagent-bootstrap"))
  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-instrumentation-api"))
  implementation(project(":javaagent-extension-api"))
  implementation("org.slf4j:slf4j-api")

  // this only exists to make Intellij happy since it doesn't (currently at least) understand our
  // inclusion of this artifact inside of :instrumentation-api
  compileOnly(project(":instrumentation-api-caching"))

  testImplementation(project(":testing-common"))
  testImplementation("com.google.guava:guava")
  testImplementation("org.assertj:assertj-core:3.19.0")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.7.2"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
