import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
	kotlin("js") version "1.4.21"
	kotlin("plugin.serialization") version "1.4.21"
}

group = "io.github.thesaminator"

repositories {
	mavenCentral()
	jcenter()
	maven {
		url = uri("https://dl.bintray.com/kotlin/kotlinx")
	}
	maven {
		url = uri("https://dl.bintray.com/kotlin/kotlin-js-wrappers")
	}
	maven {
		url = uri("https://dl.bintray.com/nwillc/maven")
	}
}

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
	implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.2")
	
	implementation("com.github.nwillc:ksvg-js:3.0.0")
}

kotlin {
	js {
		browser {
			binaries.executable()
			webpackTask {
				cssSupport.enabled = true
			}
			runTask {
				cssSupport.enabled = true
			}
		}
	}
}

tasks.withType<Kotlin2JsCompile>().configureEach {
	kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}
