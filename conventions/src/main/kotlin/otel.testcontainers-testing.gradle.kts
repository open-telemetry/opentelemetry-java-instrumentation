abstract class TestcontainersBuildService : BuildService<BuildServiceParameters.None>

val testcontainersBuildService = gradle.sharedServices.registerIfAbsent("testcontainersBuildService", TestcontainersBuildService::class.java) {
  maxParallelUsages.convention(2)
}

tasks.withType<Test>().configureEach {
  usesService(testcontainersBuildService)
}
