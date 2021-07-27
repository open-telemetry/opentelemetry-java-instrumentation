import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.champeau.jmh.JMHTask
import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("otel.java-conventions")
  id("otel.jmh-conventions")
}

dependencies {
  jmhImplementation("org.springframework.boot:spring-boot-starter-web:2.5.2")

  testImplementation("org.assertj:assertj-core")
}

tasks {

  // TODO(trask) without disabling errorprone, jmh task fails with
  //  Task :testing-overhead-jmh:jmhCompileGeneratedClasses FAILED
  //   error: plug-in not found: ErrorProne
  withType<JavaCompile>().configureEach {
    options.errorprone {
      isEnabled.set(false)
    }
  }

  named<JMHTask>("jmh") {
    val shadowTask = project(":javaagent").tasks.named<ShadowJar>("shadowJar").get()
    inputs.files(layout.files(shadowTask))

    // note: without an exporter, toSpanData() won't even be called
    // (which is good for benchmarking the instrumentation itself)
    val args = listOf(
      "-javaagent:${shadowTask.archiveFile.get()}",
      "-Dotel.traces.exporter=none",
      "-Dotel.metrics.exporter=none"
    )
    // see https://github.com/melix/jmh-gradle-plugin/issues/200
    jvmArgsPrepend.add(args.joinToString(" "))

    // TODO(trask) is this ok? if it's ok, move to otel.jmh-conventions?
    outputs.upToDateWhen { false }
  }
}
