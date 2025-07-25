plugins {
  id("otel.javaagent-instrumentation")
}

// Configuration for XJC code generation
val xjcTool by configurations.creating

val generateXjcSources = tasks.register<JavaExec>("generateXjcSources") {
  val schemaDir = file("src/test/schema")
  val outputDir = layout.buildDirectory.dir("generated/sources/xjc/java/test").get().asFile

  inputs.dir(schemaDir)
  outputs.dir(outputDir)

  classpath = xjcTool
  mainClass.set("com.sun.tools.xjc.XJCFacade")

  args(
    "-d",
    outputDir.absolutePath,
    "-p",
    "io.opentelemetry.test.hello_web_service",
    file("$schemaDir/hello.xsd").absolutePath
  )

  doFirst {
    outputDir.mkdirs()
  }
}

sourceSets {
  test {
    java {
      srcDir(generateXjcSources.map { it.outputs.files.singleFile })
    }
    resources {
      srcDirs("src/test/schema")
    }
  }
}

tasks.compileTestJava {
  dependsOn(generateXjcSources)
}

muzzle {
  pass {
    group.set("org.springframework.ws")
    module.set("spring-ws-core")
    versions.set("[2.0.0.RELEASE,]")
    // broken versions, jars don't contain classes
    skip("3.0.11.RELEASE", "3.1.0")
    assertInverse.set(true)
  }
}

tasks {
  named<Checkstyle>("checkstyleTest") {
    // exclude generated web service classes
    exclude("**/hello_web_service/**")
  }
}

dependencies {
  compileOnly("org.springframework.ws:spring-ws-core:2.0.0.RELEASE")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.boot:spring-boot-starter-web-services:2.0.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-web:2.0.0.RELEASE")

  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-web-services:2.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-web:2.+") // documented limitation

  testImplementation("wsdl4j:wsdl4j:1.6.3")
  testImplementation("com.sun.xml.messaging.saaj:saaj-impl:1.5.2")
  testImplementation("javax.xml.bind:jaxb-api:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-core:2.2.11")
  testImplementation("com.sun.xml.bind:jaxb-impl:2.2.11")
  testImplementation("com.google.guava:guava")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))

  // XJC tool dependencies
  xjcTool("com.sun.xml.bind:jaxb-xjc:2.3.3")
  xjcTool("com.sun.xml.bind:jaxb-impl:2.3.3")
  xjcTool("com.sun.xml.bind:jaxb-core:2.3.0.1")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
