package de.dfki.mary

import de.dfki.mary.tasks.GenerateConfig
import de.dfki.mary.tasks.GenerateServiceLoader
import de.dfki.mary.tasks.GenerateSource
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport

class ComponentPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply JavaLibraryPlugin
        project.pluginManager.apply GroovyPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_8

        project.extensions.create 'marytts', MaryttsExtension, project
        project.marytts {
            version = "5.2"
        }

        project.repositories {
            jcenter()
        }

        project.sourceSets {
            integrationTest {
                java {
                    compileClasspath += main.output + test.output
                    runtimeClasspath += main.output + test.output
                }
            }
        }

        project.configurations {
            integrationTestImplementation.extendsFrom testImplementation
            integrationTestRuntimeOnly.extendsFrom testRuntimeOnly
        }

        project.dependencies {
            implementation localGroovy()
            api group: 'de.dfki.mary', name: 'marytts-runtime', version: project.marytts.version, {
                exclude group: '*', module: 'groovy-all'
            }
            testImplementation group: 'org.testng', name: 'testng', version: '7.0.0'
        }

        project.tasks.register 'generateServiceLoader', GenerateServiceLoader, {
            destFile = project.layout.buildDirectory.file('serviceLoader.txt')
        }

        project.tasks.register 'generateSource', GenerateSource, {
            srcDirectory = project.file("$project.buildDir/generatedSrc/main/groovy/")
            testDirectory = project.file("$project.buildDir/generatedSrc/test/groovy/component")
            integrationTestDirectory = project.file("$project.buildDir/generatedSrc/integrationTest/groovy/component")
        }

        project.tasks.register 'generateConfig', GenerateConfig, {
            destFile = project.layout.buildDirectory.file('generated.config')
        }

        project.sourceSets {
            main {
                groovy {
                    srcDirs += project.generateSource.srcDirectory.get()
                }
            }
            test {
                groovy {
                    srcDirs += project.generateSource.testDirectory.get()
                }
            }
            integrationTest {
                groovy {
                    srcDirs += project.generateSource.integrationTestDirectory.get()
                }
            }
        }

        project.processResources {
            from project.tasks.named('generateServiceLoader'), {
                rename { 'META-INF/services/marytts.config.MaryConfig' }
            }
            from project.tasks.named('generateConfig'), {
                rename {
                    "$project.marytts.component.packagePath/${project.marytts.component.name.toLowerCase()}.config"
                }
            }
        }

        project.compileGroovy {
            dependsOn project.tasks.named('generateSource')
        }

        project.tasks.register 'integrationTest', Test, {
            group 'Verification'
            description 'Runs the integration tests.'
            workingDir = project.buildDir
            testClassesDirs = project.sourceSets.integrationTest.output.classesDirs
            classpath = project.sourceSets.integrationTest.runtimeClasspath
            systemProperty 'log4j.logger.marytts', 'INFO,stderr'
            testLogging.showStandardStreams = true
            shouldRunAfter project.tasks.named('test')
        }

        project.tasks.withType(Test) {
            useTestNG()
            testLogging {
                exceptionFormat = 'full'
            }
        }

        project.tasks.register 'testReports', TestReport, {
            reportOn project.tasks.withType(Test)
            destinationDir = project.file("$project.testReportDir/all")
        }

        project.tasks.named('check').configure {
            dependsOn project.tasks.named('testReports')
        }
    }
}
