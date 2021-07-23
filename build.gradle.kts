import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
	kotlin("js") version "1.5.10"
	kotlin("plugin.serialization") version "1.5.10"
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
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
	implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.3")
	
	//implementation("com.github.nwillc:ksvg-js:3.0.0")
}

kotlin {
	js {
		browser {
			binaries.executable()
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
		tasks.getByName("browserDistributeResources").apply {
			mustRunAfter("clean")
		},
		tasks.getByName("browserDistribution").apply {
			mustRunAfter("clean")
		}
	)
)
