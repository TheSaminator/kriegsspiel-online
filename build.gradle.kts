import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
	kotlin("js") version "1.5.31"
	kotlin("plugin.serialization") version "1.5.31"
}

group = "io.github.thesaminator"

repositories {
	mavenCentral()
	maven {
		url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
	}
	//maven {
	//	url = uri("https://dl.bintray.com/nwillc/maven")
	//}
}

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0-RC")
	implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.3")
	
	//implementation("com.github.nwillc:ksvg-js:3.0.0")
	
	//implementation(npm("@types/hammerjs", "2.0.40"))
}

kotlin {
	js(IR) {
		browser {
			binaries.executable()
			@Suppress("EXPERIMENTAL_API_USAGE")
			distribution {
				directory = File("$projectDir/publish/")
			}
		}
	}
}

tasks.getByName<Delete>("clean") {
	delete(files("$projectDir/publish/"))
}

tasks.withType<Kotlin2JsCompile>().configureEach {
	kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.create(
	"name" to "cleanAndBuild",
	"group" to "application",
	"dependsOn" to listOf(
		tasks.getByName("clean"),
		tasks.getByName("browserDistribution").apply {
			mustRunAfter("clean")
		}
	)
)
