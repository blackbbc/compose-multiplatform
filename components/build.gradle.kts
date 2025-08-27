import android.databinding.tool.ext.capitalizeUS

plugins {
    kotlin("multiplatform") apply false
    id("com.android.library") apply false
//    id("org.jetbrains.kotlinx.binary-compatibility-validator") apply false
    alias(libs.plugins.composeCompiler) apply false
}

val platforms = findProperty("compose.platforms")?.toString() ?: ""
val parsedPlatforms = ComposePlatforms.parse(platforms)

subprojects {
    version = findProperty("deploy.version")!!

    plugins.withId("java") {
        configureIfExists<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11

            withJavadocJar()
            withSourcesJar()
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
        kotlinOptions.jvmTarget = "11"
    }

    plugins.withId("maven-publish") {
        configureIfExists<PublishingExtension> {
            repositories {
                maven {
                    name = "Remote"
                    setUrl(findProperty("maven.remote.url").toString())
                    credentials {
                        username = findProperty("maven.remote.username")?.toString()
                        password = findProperty("maven.remote.password")?.toString()

                        if (username.isNullOrBlank() || password.isNullOrBlank()) {
                            logger.warn("""
                                |Maven credentials are not properly configured.
                                |Get them here: ${findProperty("maven.remote.manager.url")}.
                            """.trimMargin())
                        }
                    }
                }
            }

            val configurePublishComposeJb = { repositoryName: String ->
                tasks.register("publishComposeJbTo${repositoryName}") {
                    group = "publishing"

                    parsedPlatforms.forEach {
                        val taskName = "publish${it.name}PublicationTo${repositoryName}"
                        val task = tasks.findByName(taskName)
                        if (task == null) {
                            logger.warn("Cannot find task '$taskName' in project '${project.name}'")
                        } else {
                            dependsOn(task)
                        }
                    }
                }
            }

            configurePublishComposeJb("MavenLocal")
            repositories.all {
                configurePublishComposeJb(name.capitalizeUS())
            }
        }
    }

    afterEvaluate {
        // Call this to fix compose version for platforms not to be published.
        ComposePlatforms.handleBootstrapComposeVersion(this, parsedPlatforms)
    }
}

fun findPropertyOrEmpty(key: String): Any = findProperty(key) ?: ""