package toxic.slack.command

import org.apache.log4j.*
import groovy.json.*

class AssignCommand extends BaseCommand {
  private static Logger log = Logger.getLogger(this)

  private static assignToken = '%%'

  public AssignCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (args[0] == 'help') {
      def help = new StringBuffer()
      help << 'Assigns items defined in the topic of the current channel to the active users in this channel. ' 
      help << 'Items are defined within matching %% blocks, one item per line.' + '\n\n'
      help << 'Example:' + '\n'
      help << '```%%' + '\n'
      help << 'item1' + '\n'
      help << 'item2' + '\n'
      help << 'item3' + '\n'
      help << '%%```' + '\n'
      help << 'NOTE: Topics are limited to 250 characters.'
      return help.toString()
    }

    // Get current channel info
    def chan = bot.getChannelInfo(msg.channel)

    // Read the topic and scan for list of items:
    def items = itemsFromTopic(chan.topic.value)

    // Get users to distribute assignment
    def channelMembers = chan.members.collect{ bot.findUser(it) }
    def validUsers = channelMembers.findAll { u -> shouldBeAssigned(u, bot.getUserStatus(u.id))}
    def users = validUsers.collect { u -> "<@${u.id}>" }

    // Perform assignment
    return assign(items, users).findAll{ it.value }.collect { k,v -> "${k}: ${v}" }.join('\n')
  }

  private boolean shouldBeAssigned(user, status) {
    !user.is_bot && status.presence == 'active'
  }

  private List itemsFromTopic(String topic) {
    def capture = false
    def items = []

    topic.eachLine { line ->
      if (line != assignToken) {
        if (capture) {
          items << line
        }
      }

      if (line == assignToken) {
        capture = !capture
      }
    }

    return items
  }

  private Map assign(List items, List users) {
    def assignments = [:]
    items.each { item -> assignments[item] = null }

    if (!items || !users) {
      return assignments
    }

    int itemsPerUser = items.size() / users.size()
    int remainingItems = items.size() % users.size()
    int maxItemsPerUser = itemsPerUser ? itemsPerUser + remainingItems % users.size() : 1

    while (!assignments.every { it.value }) {
      def user = users[new Random().nextInt(users.size())]

      if (assignments.count { it.value == user} < maxItemsPerUser) {
        assignments.find { !it.value }.value = user
      }
    }

    return assignments
  }
}

