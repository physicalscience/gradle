/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.mvnsettings

import org.gradle.util.TemporaryFolder

import spock.lang.Specification
import org.junit.Rule

class DefaultLocalMavenRepositoryLocatorTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    SimpleMavenFileLocations locations
    DefaultLocalMavenRepositoryLocator locator

    Map systemProperties = ["sys.prop": "sys/prop/value"]
    Map environmentVariables = [ENV_VAR: "env/var/value"]

    File repo1 = new File("/path/to/repo1")
    File repo2 = new File("path/to/repo2")

    def setup() {
        locations = new SimpleMavenFileLocations()
        locator = new DefaultLocalMavenRepositoryLocator(locations, systemProperties, environmentVariables)
    }

    def "returns default location if no settings file exists"() {
        expect:
        // this default comes from DefaultMavenSettingsBuilder which uses System.getProperty() directly
        locator.localMavenRepository == new File("${System.getProperty("user.home")}/.m2/repository")
    }

    def "honors location specified in user settings file"() {
        writeSettingsFile(locations.userSettingsFile, repo1)

        expect:
        locator.localMavenRepository == repo1
    }

    def "honors location specified in global settings file"() {
        writeSettingsFile(locations.globalSettingsFile, repo1)

        expect:
        locator.localMavenRepository == repo1
    }

    def "prefers location specified in user settings file over that in global settings file"() {
        writeSettingsFile(locations.userSettingsFile, repo1)
        writeSettingsFile(locations.globalSettingsFile, repo2)

        expect:
        locator.localMavenRepository == repo1
    }

    def "handles the case where (potential) location of global settings file cannot be determined"() {
        locations.globalSettingsFile = null

        expect:
        locator.localMavenRepository == new File("${System.getProperty("user.home")}/.m2/repository")

        when:
        writeSettingsFile(locations.userSettingsFile, repo1)

        then:
        locator.localMavenRepository == repo1
    }

    def "replaces placeholders for system properties and environment variables"() {
        writeSettingsFile(locations.userSettingsFile, '/a/b/${sys.prop}/${env.ENV_VAR}')

        expect:
        locator.localMavenRepository == new File("/a/b/sys/prop/value/env/var/value")
    }

    private void writeSettingsFile(File settings, File repo) {
        writeSettingsFile(settings, repo.absolutePath)
    }

    private void writeSettingsFile(File settings, String repo) {
        settings << """
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <localRepository>$repo</localRepository>
</settings>"""
    }

    private class SimpleMavenFileLocations implements MavenFileLocations {
        File userMavenDir
        File globalMavenDir
        File userSettingsFile = tmpDir.file("userSettingsFile")
        File globalSettingsFile = tmpDir.file("globalSettingsFile")
    }
}
