package toxic.job

import org.junit.*
import groovy.mock.interceptor.*

public class HgRepositoryTest {

  @Test(expected=HgCommandException)
  public void should_throw_exception_if_command_returns_error() {
    withRuntimeMock(0, null, "some error") {
      new HgRepository("foo", "bar", null, null).exec("foo")
    }
  }

  @Test
  public void should_fail_silently_if_specified() {
    withRuntimeMock(0, null, "some error") {
      new HgRepository("foo", "bar", null, null).exec("foo", true)
    }
  }

  @Test
  public void should_determine_if_repo_needs_to_be_updated() {
    def nochanges = """comparing with https://repo.mycompany.invalid/repos/toxic
searching for changes
no changes found"""
      
    withRuntimeMock(0, nochanges, null) {
      assert !new HgRepository("foo", "bar", null, null).hasChanges()
    }

    withRuntimeMock(0, "", null) {
      assert !new HgRepository("foo", "bar", null, null).hasChanges()
    }

    def incoming = new StringBuffer()
    incoming.append("changeset:   15:7f57dba7be6c")
    incoming.append("user:        Magic Johnson")
    incoming.append("date:        Wed May 27 15:50:23 2015 -0400")
    incoming.append("summary:     Slam dunk")

    withRuntimeMock(0, incoming.toString(), null) {
      assert new HgRepository("foo", "bar", null, null).hasChanges()
    }    
  }

  @Test
  public void should_collect_revisions() {
    def keyword = "some"

    def incoming = """comparing with https://repo.mycompany.invalid/repos/myrepo
searching for changes
changeset:   6253:06c1fbbfd7c5
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 11:14:14 2015 -0400
summary:     some big change

changeset:   6254:b6033c5c1848
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 13:41:19 2015 -0400
summary:     some big change

changeset:   6255:74106f313f2c
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 15:29:57 2015 -0400
summary:     some big change
"""

    withRuntimeMock(0, incoming.toString(), null) {
      assert new HgRepository("foo", "bar", null, null).getRevisionsByKeyword(keyword).size() == 3
    }
  }

  @Test
  public void should_update_local_repository() {
    withRuntimeMock(0, "Ok!", null) {
      assert !new HgRepository("foo", "bar", null, null).update()
    }
    
    def incoming = """comparing with https://repo.mycompany.invalid/repos/myrepo
searching for changes
changeset:   6253:06c1fbbfd7c5
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 11:14:14 2015 -0400
summary:     some big change

changeset:   6254:b6033c5c1848
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 13:41:19 2015 -0400
summary:     some big change

changeset:   6255:74106f313f2c
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 15:29:57 2015 -0400
summary:     some big change

changeset:   6256:6e5001e9004c
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 09:03:21 2015 -0400
summary:     some big change

changeset:   6257:4b5e130b3cd8
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 16:52:14 2015 -0400
summary:     some big change

changeset:   6258:57f98916094d
user:        jsmith <jsmith@mycompany.invalid>
date:        Thu Jun 18 17:23:16 2015 -0400
summary:     some big change

changeset:   6259:340ccb565aff
user:        jsmith <jsmith@mycompany.invalid>
date:        Fri Jun 19 15:06:50 2015 -0400
summary:     some big change

changeset:   6260:eb7c5b105240
tag:         release_2.6.680
user:        jsmith <jsmith@mycompany.invalid>
date:        Mon Jun 22 10:24:09 2015 -0400
summary:     some big change

changeset:   6261:f8572d2b40b4
date:        Tue Jun 23 14:48:38 2015 -0400
summary:     some big change

changeset:   6262:c8e599eaa15b
user:        jsmith <jsmith@mycompany.invalid>
date:        Tue Jun 23 14:49:10 2015 -0400
summary:     some big change

changeset:   6263:c22389b1c856
tag:         tip
user:        jsmith
date:        Tue Jun 23 15:40:41 2015 -0400
summary:     some big change

"""
    withRuntimeMock(0, incoming.toString(), null) {
      def changes = new HgRepository("foo", "bar", null, null).update()
      assert changes.size() == 11
      assert changes[0].changeset == "6253:06c1fbbfd7c5"
      assert changes[0].changesetUrl == "bar/rev/06c1fbbfd7c5"
      assert changes[0].user == "jsmith"
      assert changes[0].name == "jsmith"
      assert changes[0].email == "jsmith@mycompany.invalid"
      assert changes[0].date == "Thu Jun 18 11:14:14 2015 -0400"
      assert changes[0].summary == "some big change"
      assert !changes[8].user
      assert !changes[8].name
      assert !changes[8].email
      assert changes[9].user == "jsmith"
      assert changes[9].name == "jsmith"
      assert changes[9].email == "jsmith@mycompany.invalid"
      assert changes[10].changeset == "6263:c22389b1c856"
      assert changes[10].changesetUrl == "bar/rev/c22389b1c856"
      assert changes[10].tag == "tip"
      assert changes[10].user == "jsmith"
      assert changes[10].name == "jsmith"
      assert !changes[10].email
      assert changes[10].date == "Tue Jun 23 15:40:41 2015 -0400"
      assert changes[10].summary == "some big change"
    }
  }
  
  @Test
  public void should_build_changeset_url_from_template() {

    def sb = new StringBuilder()
    sb.append('comparing with https://repo.mycompany.com/repos/myrepo' + '\n')
    sb.append('searching for changes' + '\n')
    sb.append('changeset:   6253:06c1fbbfd7c5' + '\n')
    sb.append('user:        jsmith <jsmith@mycompany.invalid>' + '\n')
    sb.append('date:        Thu Jun 18 11:14:14 2015 -0400' + '\n')
    sb.append('summary:     some big change')

    def incoming = sb.toString()

    withRuntimeMock(0, incoming, null) {

      def scenarios = [
        [template: null, expected: 'bar/rev/06c1fbbfd7c5'],
        [template: 'http://go/@@wrongrepo@@/@@wrongcommit@@', expected: 'http://go/@@wrongrepo@@/@@wrongcommit@@'],
        [template: 'http://go/@@repo@@/@@changeset@@', expected: 'http://go/bar/06c1fbbfd7c5']
      ]

      scenarios.each { sc ->
        new HgRepository("foo", "bar", sc.template, null).collectChanges().with { changes ->
          assert changes[0].changesetUrl == sc.expected
        }
      }
    }
  }

  @Test
  public void should_get_diff() {
    def mockDiff = 'some output'

    withRuntimeMock(0, mockDiff, null) {
      new HgRepository("foo", "bar", null, null).getDiff('12345').with { diff ->
        assert diff == mockDiff
      }
    }
  }

  private withRuntimeMock(exitValue, out, err, closure) {
    def stub = [:]
    stub.exitValue = { -> exitValue }
    stub.waitForProcessOutput = { o,e -> 
      if (out) o.append(out)
      if (err) e.append(err)
    }

    Runtime.metaClass.exec = { String cmd -> stub }
    closure.call()
    Runtime.metaClass = null
  }

  @Test
  public void should_equal() {
    def repo1 = new HgRepository("l1", "l2", "l3", "l4")
    assert !repo1.equals(null)
    assert !repo1.equals(new HgRepository(null, null, "l3"))
    assert !repo1.equals(new HgRepository("l1", null, "l3"))
    assert !repo1.equals(new HgRepository("l1", "l2", "l3"))
    assert !repo1.equals(new HgRepository("l1", "l2", "l3", "tt"))
    assert repo1.equals(repo1)
  }

  @Test
  public void should_match_hash() {
    assert new HgRepository("l1", "l2", "l3", "l4").hashCode() == -1154497581
    assert new HgRepository("l1", "l2", "l3").hashCode() == -982024554
    assert new HgRepository("l1", null, null).hashCode() == -888389284
    assert new HgRepository(null, null, null).hashCode() == 1544803905
  }
 }