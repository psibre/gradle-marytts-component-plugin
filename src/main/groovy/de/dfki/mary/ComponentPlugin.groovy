package de.dfki.mary

import de.dfki.mary.tasks.GenerateConfig
import de.dfki.mary.tasks.GenerateServiceLoader
import de.dfki.mary.tasks.UnpackSourceTemplates
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestReport

class ComponentPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaLibraryPlugin)
        project.pluginManager.apply(GroovyPlugin)

        project.java.sourceCompatibility = JavaVersion.VERSION_1_8

        project.extensions.create('marytts', MaryttsExtension, project)
        project.marytts {
            version = "5.2.1"
        }

        project.repositories {
            mavenCentral()
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
            api group: 'de.dfki.mary', name: 'marytts-runtime', version: project.marytts.version, {
                exclude group: '*', module: 'groovy-all'
                exclude group: 'com.twmacinta', module: 'fast-md5'
                exclude group: 'gov.nist.math', module: 'Jampack'
            }
            testImplementation localGroovy()
            testImplementation group: 'org.testng', name: 'testng', version: '7.5.1'
            constraints {
                implementation('de.dfki.lt.jtok:jtok-core:1.9.4') {
                    because 'transitive dependency v1.9.3 is not available in Maven Central'
                }
            }
        }

        project.tasks.register('generateServiceLoader', GenerateServiceLoader) {
            group = 'MaryTTS Component'
            destFile.set project.layout.buildDirectory.file('serviceLoader.txt')
        }

        def unpackSourceTemplatesTask = project.tasks.register('unpackSourceTemplates', UnpackSourceTemplates) {
            group = 'MaryTTS Component'
            resourceNames.add 'ConfigClass.java'
            destDir.set project.layout.buildDirectory.dir('unpackedSrcTemplates')
        }

        def unpackTestSourceTemplatesTask = project.tasks.register('unpackTestSourceTemplates', UnpackSourceTemplates) {
            group = 'MaryTTS Component'
            resourceNames.add 'ConfigTest.groovy'
            destDir.set project.layout.buildDirectory.dir('unpackedTestSrcTemplates')
        }

        def unpackIntegrationTestSourceTemplatesTask = project.tasks.register('unpackIntegrationTestSourceTemplates', UnpackSourceTemplates) {
            group = 'MaryTTS Component'
            resourceNames.add 'IntegrationTest.groovy'
            destDir.set project.layout.buildDirectory.dir('unpackedIntegrationTestSrcTemplates')
        }

        def generateSourceTask = project.tasks.register('generateSource', Copy) {
            group = 'MaryTTS Component'
            into project.layout.buildDirectory.dir('generatedSrc')
            from unpackSourceTemplatesTask
            eachFile { file ->
                if (file.name == 'ConfigClass.java')
                    file.name = "${project.marytts.component.name}Config.java"
                file.path = "$project.marytts.component.packagePath/$file.name"
            }
            expand project.properties
        }

        def generateTestSourceTask = project.tasks.register('generateTestSource', Copy) {
            group = 'MaryTTS Component'
            into project.layout.buildDirectory.dir('generatedTestSrc')
            from unpackTestSourceTemplatesTask
            eachFile { file ->
                if (file.name == 'ConfigTest.groovy')
                    file.name = "${project.marytts.component.name}ConfigTest.groovy"
                file.path = "$project.marytts.component.packagePath/$file.name"
            }
            expand project.properties
        }

        def generateIntegrationTestSourceTask = project.tasks.register('generateIntegrationTestSource', Copy) {
            group = 'MaryTTS Component'
            into project.layout.buildDirectory.dir('generatedIntegrationTestSrc')
            from unpackIntegrationTestSourceTemplatesTask
            eachFile { file ->
                if (file.name == 'IntegrationTest.groovy')
                    file.name = "Load${project.marytts.component.name}IT.groovy"
                file.path = "$project.marytts.component.packagePath/$file.name"
            }
            expand project.properties
        }

        project.tasks.register('generateConfig', GenerateConfig) {
            group = 'MaryTTS Component'
            destFile.set project.layout.buildDirectory.file('generated.config')
        }

        project.sourceSets {
            main.java.srcDir generateSourceTask
            test.groovy.srcDir generateTestSourceTask
            integrationTest.groovy.srcDir generateIntegrationTestSourceTask
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

        project.tasks.register('integrationTest', Test) {
            group = 'Verification'
            description = 'Runs the integration tests.'
            workingDir = project.buildDir
            testClassesDirs = project.sourceSets.integrationTest.output.classesDirs
            classpath = project.sourceSets.integrationTest.runtimeClasspath
            systemProperty 'log4j.logger.marytts', 'INFO,stderr'
            testLogging.showStandardStreams = true
            shouldRunAfter project.tasks.named('test')
        }

        project.tasks.withType(Test).configureEach {
            useTestNG()
            testLogging {
                exceptionFormat = 'full'
            }
        }

        project.tasks.register('testReports', TestReport) {
            testResults.from project.tasks.withType(Test).collect { it.binaryResultsDirectory }
            destinationDirectory = project.file("$project.testReportDir/all")
        }

        project.tasks.named('check').configure {
            dependsOn project.tasks.named('testReports')
        }
    }
}
