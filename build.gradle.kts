import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
	id("org.springframework.boot") version "3.2.0"
	id("io.spring.dependency-management") version "1.1.4"
	kotlin("jvm") version "1.9.20"
	kotlin("plugin.spring") version "1.9.20"
	id("org.jetbrains.dokka") version "1.9.10"
	id("maven-publish")
}

buildscript {
	dependencies {
		classpath("org.jetbrains.dokka:dokka-base:1.9.10")
	}
}

group = "shark"
version = "1.0.0"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven {
		name = "Jitpack"
		url = URI("https://jitpack.io")
	}
}

dependencies {
	// Spring
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.session:spring-session-core")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")

	// Application
	implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
	implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
	implementation("com.google.guava:guava:32.1.3-jre")
	implementation("io.kpeg:kpeg:0.1.2")
	implementation("net.dv8tion:JDA:5.0.0-beta.15")
	implementation("de.undercouch:bson4jackson:2.15.0")
	implementation("com.j2html:j2html:1.6.0")
	implementation("com.auth0:java-jwt:4.4.0")
	implementation("org.jsoup:jsoup:1.15.4")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC2")
	implementation("org.thymeleaf:thymeleaf:3.1.2.RELEASE")

}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = java.sourceCompatibility.toString()
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

configure<PublishingExtension> {
	publications.create<MavenPublication>("maven") {
		from(components.getByName("kotlin"))
	}
}

tasks.withType<ProcessResources> {
	val resourceTargets = listOf("META-INF/shark.yml")
	val replaceProperties = mapOf(
		Pair(
			"gradle",
			mapOf(
				Pair("gradle", gradle),
				Pair("project", project)
			)
		)
	)
	filesMatching(resourceTargets) {
		expand(replaceProperties)
	}
}

tasks.dokkaHtml {
	pluginConfiguration<org.jetbrains.dokka.base.DokkaBase, org.jetbrains.dokka.base.DokkaBaseConfiguration> {
		footerMessage = "Copyright © StarWorld Team"
	}
}
