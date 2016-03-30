/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.notations

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.impldeps.GradleImplDepsProvider
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.FileLockManager
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.installation.GradleInstallation
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.impldeps.GradleImplDepsProvider.CACHE_DISPLAY_NAME
import static org.gradle.api.internal.impldeps.GradleImplDepsProvider.CACHE_KEY
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

public class DependencyClassPathNotationConverterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def instantiator = Mock(Instantiator)
    def classPathRegistry = Mock(ClassPathRegistry)
    def fileResolver = Mock(FileResolver)
    def cacheRepository = Mock(CacheRepository)
    def cacheBuilder = Mock(CacheBuilder)
    def cache = Mock(PersistentCache)
    def progressLoggerFactory = Mock(ProgressLoggerFactory)

    def "parses classpath literals"() {
        given:
        def dependency = Mock(SelfResolvingDependency)
        def gradleApiFileCollection = Mock(FileCollectionInternal)
        def gradleApiClasspath = Mock(ClassPath)
        def gradleApiFiles = [new File('foo')]
        def localGroovyClasspath = Mock(ClassPath)
        def localGroovyFileCollection = Mock(FileCollectionInternal)
        def localGroovyFiles = [new File('bar')]
        def installationBeaconClasspath = Mock(ClassPath)
        def installationBeaconFileCollection = Mock(FileCollectionInternal)
        def installationBeaconFiles = [new File('baz')]

        and:
        classPathRegistry.getClassPath('GRADLE_API') >> gradleApiClasspath
        gradleApiClasspath.asFiles >> gradleApiFiles
        fileResolver.resolveFiles(gradleApiFiles) >> gradleApiFileCollection
        classPathRegistry.getClassPath('LOCAL_GROOVY') >> localGroovyClasspath
        localGroovyClasspath.asFiles >> localGroovyFiles
        fileResolver.resolveFiles(localGroovyFiles) >> localGroovyFileCollection
        classPathRegistry.getClassPath('GRADLE_INSTALLATION_BEACON') >> installationBeaconClasspath
        installationBeaconClasspath.asFiles >> installationBeaconFiles
        fileResolver.resolveFiles(installationBeaconFiles) >> installationBeaconFileCollection

        instantiator.newInstance(DefaultSelfResolvingDependency.class, _) >> dependency

        when:
        def gradleImplDepsProvider = new GradleImplDepsProvider(cacheRepository, progressLoggerFactory, GradleVersion.current().version)
        def factory = new DependencyClassPathNotationConverter(instantiator, classPathRegistry, fileResolver, gradleImplDepsProvider, new CurrentGradleInstallation(new GradleInstallation(testDirectoryProvider.file("gradle-home"))))
        def out = parse(factory, DependencyFactory.ClassPathNotation.GRADLE_API)

        then:
        1 * cacheRepository.cache(CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        1 * cache.useCache(_, _)
        out.is dependency

        when: // same instance is reused
        def out2 = parse(factory, DependencyFactory.ClassPathNotation.GRADLE_API)

        then:
        0 * instantiator._
        out2.is out
    }

    def parse(def factory, def value) {
        return NotationParserBuilder.toType(Dependency).fromType(DependencyFactory.ClassPathNotation, factory).toComposite().parseNotation(value)
    }

}