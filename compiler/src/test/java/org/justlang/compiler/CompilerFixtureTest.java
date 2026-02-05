package org.justlang.compiler;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

public class CompilerFixtureTest {
    @TestFactory
    Stream<DynamicTest> compilerEndToEndFixtures() throws IOException {
        List<CompilerFixtureKit.FixtureCase> fixtures = CompilerFixtureKit.discoverFixtures();
        return fixtures.stream()
            .map(fixture -> DynamicTest.dynamicTest(
                fixture.name(),
                () -> CompilerFixtureKit.executeFixture(fixture)
            ));
    }
}
