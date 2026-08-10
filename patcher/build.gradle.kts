plugins {
  id("buildsrc.convention.kotlin-jvm")
  id("buildsrc.convention.spotless")
  id("buildsrc.convention.sonarlint")
  id("buildsrc.common.keys")
}

dependencies {
  implementation(libs.bundles.crypto)
  testImplementation(libs.bundles.kotest)
}

tasks.register<JavaExec>("run") {
  group = "application"
  description = "Patches the PokeMMO client and runs the patched copy"

  val patchedExecutable = layout.buildDirectory.file("PokeMMO-openmmo.exe")

  mainClass.set("de.fiereu.openmmo.patcher.Launcher")
  classpath(sourceSets.main.get().runtimeClasspath)
  systemProperty("openmmo.output", patchedExecutable.get().asFile.path)
  doFirst {
    val pokemmoExecutable =
        (project.findProperty("pokemmo.executable") as String?)?.takeUnless(String::isBlank)
            ?: env.fetchOrNull("POKEMMO_EXECUTABLE")?.takeUnless(String::isBlank)
            ?: error("Set POKEMMO_EXECUTABLE in .env or pass -Ppokemmo.executable=<path>")
    val pokemmoWorkingDir =
        (project.findProperty("pokemmo.workingDir") as String?)?.takeUnless(String::isBlank)
            ?: env.fetchOrNull("POKEMMO_WORKING_DIR")?.takeUnless(String::isBlank)
            ?: error("Set POKEMMO_WORKING_DIR in .env or pass -Ppokemmo.workingDir=<path>")
    val loginHost =
        (project.findProperty("openmmo.loginHost") as String?)?.takeUnless(String::isBlank)
            ?: env.fetchOrNull("OPENMMO_LOGIN_HOST")?.takeUnless(String::isBlank)
            ?: "127.0.0.1"
    systemProperty("openmmo.executable", pokemmoExecutable)
    systemProperty("openmmo.workingDir", pokemmoWorkingDir)
    systemProperty("openmmo.loginHost", loginHost)
  }
  maxHeapSize = "1g"
}

tasks.register<JavaExec>("patchAndroidSmali") {
  group = "openmmo"
  description = "Patches a PokeMMO APK directory decoded by apktool"
  dependsOn("classes")
  mainClass.set("de.fiereu.openmmo.patcher.AndroidSmaliPatcher")
  classpath(sourceSets.main.get().runtimeClasspath)
  val input = providers.gradleProperty("openmmo.android.apktoolDirectory")
  val feedOrigin = providers.gradleProperty("openmmo.android.feedOrigin")
  doFirst { args(input.get(), feedOrigin.get()) }
}

tasks.register<JavaExec>("patchStandaloneClient") {
  group = "openmmo"
  description = "Patches a native desktop client to use the public OpenMMO feed"
  dependsOn("classes")
  mainClass.set("de.fiereu.openmmo.patcher.StandaloneClientPatcher")
  classpath(sourceSets.main.get().runtimeClasspath)
  val input = providers.gradleProperty("openmmo.standalone.input")
  val output = providers.gradleProperty("openmmo.standalone.output")
  val feedOrigin = providers.gradleProperty("openmmo.standalone.feedOrigin")
  doFirst { args(input.get(), output.get(), feedOrigin.get()) }
}

listOf("classes", "processResources").forEach { taskName ->
  tasks.named(taskName) { dependsOn("copyPublicKeys", "copyPrivateKeyFeed") }
}
