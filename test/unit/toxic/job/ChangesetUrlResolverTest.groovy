package toxic.job

import org.junit.*

class ChangesetUrlResolverTest {

  @Test
  public void should_resolve_changeset_url() {
    def scenarios = [
      [ 
        template: null, 
        changeset: 'be5536bc7af259172acf14eaee3feb2e8f57d271', 
        repo: 'foo',
        expected: ''
      ],
      [ 
        template: 'http://go/@@wrongrepo@@/@@wrongcommit@@', 
        changeset: 'be5536bc7af259172acf14eaee3feb2e8f57d271',
        repo: 'foo',
        expected: 'http://go/@@wrongrepo@@/@@wrongcommit@@'
      ],
      [ 
        template: 'http://go/@@repo@@/@@changeset@@', 
        changeset:'be5536bc7af259172acf14eaee3feb2e8f57d271', 
        repo: 'foo',
        expected: 'http://go/foo/be5536bc7af259172acf14eaee3feb2e8f57d271'
      ]
    ]

    def resolver = new ChangesetUrlResolver()

    scenarios.each { sc ->
      assert resolver.resolve(sc.template, sc.repo, sc.changeset) == sc.expected
    }
  }
}
