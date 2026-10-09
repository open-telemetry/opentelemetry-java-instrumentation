val stableVersion = "3.0.0-SNAPSHOT"
val alphaVersion = "3.0.0-alpha-SNAPSHOT"

val apidiffBaselineVersion = "2.32.0"

allprojects {
  val stable = generateSequence(this) { it.parent }
    .map { it.extensions.extraProperties }
    .firstOrNull { it.has("otel.stable") }
    ?.get("otel.stable")
  if (stable != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
  extra["apidiffBaselineVersion"] = apidiffBaselineVersion
}
