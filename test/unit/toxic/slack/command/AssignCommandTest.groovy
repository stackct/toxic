package toxic.slack.command

import org.junit.*

class AssignCommandTest extends CommandTest {
  def command = new AssignCommand()

  @Test
  public void should_provide_help() {
    def expected = new StringBuffer() 
    expected << 'Assigns items defined in the topic of the current channel to the active users in this channel. '
    expected << 'Items are defined within matching %% blocks, one item per line.' + '\n\n'
    expected << 'Example:' + '\n'
    expected << '```%%' + '\n'
    expected << 'item1' + '\n'
    expected << 'item2' + '\n'
    expected << 'item3' + '\n'
    expected << '%%```' + '\n'
    expected << 'NOTE: Topics are limited to 250 characters.'

    assert expected.toString() == command.handle(['help'], bot, [:])
  }

  @Test
  public void should_filter_bot_and_inactive_users() {
    bot.rtm.users << [id: 'W0000001', name: 'THEBOT', is_bot: true, status: [ok:true, presence:'active']]
    bot.rtm.users << [id: 'W0000002', name: 'USER1', is_bot: false, status: [ok:true, presence:'active']]
    bot.rtm.users << [id: 'W0000003', name: 'USER2', is_bot: false, status: [ok:true, presence:'active']]
    bot.rtm.users << [id: 'W0000004', name: 'USER3', is_bot: false, status: [ok:true, presence:'away']]
    bot.metaClass.getUserStatus = { id -> bot.rtm.users.find{it.id == id}.status }

    def chan = [ id: 'C12345', name: 'lobby', is_group: true, topic:[value:''] ]
    chan.members = bot.rtm.users.collect { it.id}
    chan.topic.value += '%%' + '\n'
    chan.topic.value += 'foo' + '\n'
    chan.topic.value += 'bar' + '\n'
    chan.topic.value += '%%' + '\n'
    bot.rtm.channels << chan
    bot.metaClass.getChannelInfo = { id -> chan }

    def actual = command.handle([], bot, [channel:'C12345'])

    actual.eachLine { line ->
      assert (line ==~ /(foo|bar): <@(W0000002|W0000003)>/)
    }
  }

  @Test
  public void should_assign_items_to_users() {
    def testCases = [
      [
        name: "more users than items",
        items: ['item1', 'item2'], 
        users: ['fred', 'barney', 'wilma', 'betty'], 
        expected: [
          fred:   { assigned -> assigned == 0 || assigned == 1 },
          barney: { assigned -> assigned == 0 || assigned == 1 },
          wilma:  { assigned -> assigned == 0 || assigned == 1 },
          betty:  { assigned -> assigned == 0 || assigned == 1 },
        ],
        allUsersAssigned: false, 
        allItemsAssigned: true
      ],
      [
        name: "more items than users",
        items: ['item1', 'item2', 'item3'], 
        users: ['fred', 'barney'], 
        expected: [
          fred:   { assigned -> assigned == 1 || assigned == 2 },
          barney: { assigned -> assigned == 1 || assigned == 2 },
        ],
        allUsersAssigned: true, 
        allItemsAssigned: true
      ],
      [
        name: "equal number of items and users",
        items: ['item1', 'item2', 'item3'], 
        users: ['fred', 'barney', 'wilma'], 
        expected: [
          fred:   { assigned -> assigned == 1 },
          barney: { assigned -> assigned == 1 },
          wilma:  { assigned -> assigned == 1 },
        ],
        allUsersAssigned: true, 
        allItemsAssigned: true
      ],
      [
        name: "items, but no users",
        items: ['item1', 'item2', 'item3', 'item4', 'item5'], 
        users: [], 
        expected: [:],
        allUsersAssigned: true, 
        allItemsAssigned: false
      ],
      [
        name: "users, but no items",
        items: [], 
        users: ['fred', 'barney', 'wilma', 'betty'], 
        expected: [
          fred:   { assigned -> assigned == 0 },
          barney: { assigned -> assigned == 0 },
          wilma:  { assigned -> assigned == 0 },
          betty:  { assigned -> assigned == 0 },
        ],
        allUsersAssigned: false, 
        allItemsAssigned: true
      ],
      [
        name: "no users or items",
        items: [], 
        users: [], 
        expected: [:],
        allUsersAssigned: true, 
        allItemsAssigned: true
      ]
    ]

    testCases.each { tc -> 
      def assignments = command.assign(tc.items, tc.users)
      def assigned = assignments.groupBy { it.value }.collectEntries { user, items -> [(user): items.size()] }

      assigned.findAll { user, count -> user != null }.each { user, count -> 
        assert tc.expected[(user)]?.call(count)
      }

      assert allUsersAssigned(tc.users, assignments) == tc.allUsersAssigned, "allUsersAssigned failed for '${tc.name}'; assignment=${assignments}"
      assert allItemsAssigned(tc.items, assignments) == tc.allItemsAssigned, "allItemsAssigned failed for '${tc.name}'; assignment=${assignments}"
    }
  }

  private boolean allUsersAssigned(List users, Map assignments) {
    assignments.collect { k,v -> v }.containsAll(users)
  }

  private boolean allItemsAssigned(List items, Map assignments) {
    assignments.findAll { k,v -> v }.collect { k,v -> k } == items
  }

  @Test
  public void should_extract_items_from_topic() {
    def topic = new StringBuffer()
    topic << 'Some general information' + '\n'
    topic << '%%' + '\n'
    topic << 'item1' + '\n'
    topic << 'item2' + '\n'
    topic << 'item3' + '\n'
    topic << 'item4' + '\n'
    topic << '%%' + '\n'
    topic << 'Thank you, and please drive through.' + '\n'

    assert ['item1', 'item2', 'item3', 'item4'] == command.itemsFromTopic(topic.toString())
  }
}
