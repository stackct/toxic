package toxic.slack.command

import org.junit.*

import toxic.slack.*

public class CommandFactoryTest {

  def handler

  @Before
  public void before() {
    handler = [:] as SlackHandler
  }

  @Test(expected=InvalidCommand)
  public void should_throw_exception_for_invalid_class() {
    CommandFactory.make("foo", handler)
  }

  @Test
  public void should_construct_commands() {
    CommandFactory.available().each { assert CommandFactory.make(it, handler) instanceof Command }
  }

  @Test
  public void should_list_available_commands() {
    assert CommandFactory.available().sort() == [
      'about',
      'ack',
      'assign',
      'bounce',
      'describe',
      'halt',
      'latest',
      'list',
      'log',
      'oncall',
      'pause',
      'resolve',
      'run',
      'silence',
      'unpause'
      ]
    }
}