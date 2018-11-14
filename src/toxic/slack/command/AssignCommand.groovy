package toxic.slack.command

import org.apache.log4j.*
import groovy.json.*
import toxic.util.Table

class AssignCommand extends BaseCommand {
  private static Logger log = Logger.getLogger(this)

  private static String assignToken
  public static List<String> assignSymbols = []

  static {
    reset()
  }

  public static reset() {
    assignToken = '%%'
    assignSymbols = [
            ':zero:',
            ':one:',
            ':two:',
            ':three:',
            ':four:',
            ':five:',
            ':six:',
            ':seven:',
            ':eight:',
            ':nine:',
            ':keycap_ten:',
            ':hash:',
            ':keycap_star:',
            ':a:',
            ':b:',
            ':o2:',
    ]
  }

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
    def assignments = assign(items, users).findAll{ it.value }
    return assignments.collect { item, user -> "${item} ${user}" }.join('\n')
  }

  private boolean shouldBeAssigned(user, status) {
    !user.is_bot && status.presence == 'active'
  }

  private List itemsFromTopic(String topic) {
    def capture = false
    def items = []

    int index = 0;
    topic.eachLine { line ->
      if (line != assignToken) {
        if (capture) {
          def symbol = (index < assignSymbols.size() ? assignSymbols[index] + ' ' : '')
          items << "${symbol}${line}"
          index++
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

    if (!items || !users) {
      return assignments
    }

    int userIndex = 0
    Random random = new Random(System.nanoTime())
    Collections.shuffle(users, random)

    items.eachWithIndex { item, index ->
      assignments[item] = users[userIndex++]
      if((index+1) % users.size() == 0) {
        userIndex = 0
        Collections.shuffle(users, random)
      }
    }

    return assignments
  }
}

