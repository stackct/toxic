package toxic.dsl

import org.junit.Test

class DepTest {
  @Test
  void should_parse_single_dep() {
    def assertDep = { input, expectedName, expectedArtifactId ->
      new Dep().with { parser ->
        def results = parser.parse(input)
        assert 1 == results.size()
        Dep dep = results[0]
        assert expectedName == dep.name
        assert expectedArtifactId == dep.artifactId
      }
    }

    assertDep('dep "ARTIFACT_NAME"', 'ARTIFACT_NAME', 'ARTIFACT_NAME')
    assertDep('dep "ARTIFACT_NAME", "alias"', 'alias', 'ARTIFACT_NAME')
  }

  @Test
  void should_parse_multiple_deps() {
    def assertDep = { Dep dep, expectedName, expectedArtifactId ->
      assert expectedName == dep.name
      assert expectedArtifactId == dep.artifactId
    }

    String input = """
                    dep "dep1"
                    dep "dep2", "alias"
                    dep "dep3"
                    """

    Dep dep = new Dep()
    def results = dep.parse(input)
    assert 3 == results.size()
    assertDep(results[0], 'dep1', 'dep1')
    assertDep(results[1], 'alias', 'dep2')
    assertDep(results[2], 'dep3', 'dep3')
  }
}
