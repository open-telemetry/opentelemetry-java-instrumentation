plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    coreJdk()
  }
}

sourceSets {
  test {
    runtimeClasspath += files("${project.layout.buildDirectory}/classes/java/main")
  }
}

// FIXME trim down as things come online
dependencies {
  bootstrap(project(":instrumentation:nocode:bootstrap"))
//  testImplementation(project(":instrumentation:nocode:bootstrap"))
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
//  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation(project(":sdk-autoconfigure-support"))
  compileOnly(project(":javaagent-tooling"))
//  compileOnly(project(":instrumentation-annotations-support"))

  compileOnly("org.snakeyaml:snakeyaml-engine:2.8")

  implementation("org.apache.commons:commons-jexl3:3.4.0") {
    exclude("commons-logging", "commons-logging")
  }
  implementation("org.slf4j:jcl-over-slf4j")

  add("codegen", project(":instrumentation:nocode:bootstrap"))
}

tasks.withType<Test>().configureEach {
  environment("SPLUNK_OTEL_INSTRUMENTATION_NOCODE_YML_FILE", "./src/test/config/nocode.yml")
}
