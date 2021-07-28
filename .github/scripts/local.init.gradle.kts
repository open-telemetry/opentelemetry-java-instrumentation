addListener(object : BuildAdapter() {
  override fun projectsEvaluated(gradle: Gradle) {
    gradle.rootProject {
      repositories {
        mavenLocal()
        removeIf { it.name == "sonatype" }
      }
    }
  }
})
