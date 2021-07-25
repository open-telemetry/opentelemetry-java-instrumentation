import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("otel.java-conventions")
  id("otel.jmh-conventions")
}

val agentJar by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  jmhImplementation("org.springframework.boot:spring-boot-starter-web:2.5.2")
  jmhImplementation(project(":testing-common")) {
    exclude("ch.qos.logback")
  }

  // this only exists to make Intellij happy since it doesn't (currently at least) understand our
  // inclusion of this artifact inside of :testing-common
  jmhCompileOnly(project(path = ":testing:armeria-shaded-for-testing", configuration = "shadow"))

  agentJar(project(path = ":javaagent", configuration = "shadow")) {
    isTransitive = false
  }
}

tasks {
  val copyAgent by registering(Copy::class) {
    into(file("$buildDir/agent"))
    from(configurations.named("agentJar"))
    rename { file: String ->
      file.replace("-$version", "")
    }

    dependsOn(":javaagent:shadowJar")
  }

  // TODO(trask) without disabling errorprone, jmh task fails with
  //  Task :testing-overhead-jmh:jmhCompileGeneratedClasses FAILED
  //   error: plug-in not found: ErrorProne
  withType<JavaCompile>().configureEach {
    options.errorprone {
      isEnabled.set(false)
    }
  }

  named<me.champeau.jmh.JMHTask>("jmh") {
    dependsOn(copyAgent)

    // TODO(trask) is this ok? if it's ok, move to otel.jmh-conventions?
    outputs.upToDateWhen { false }
  }
}
