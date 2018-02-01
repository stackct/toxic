package toxic.user

import toxic.job.JobManager
import toxic.job.ConfigManager
import org.apache.log4j.*


@Singleton
class UserManager {
  private static Logger log = Logger.getLogger(this)

  private String configDir
  private boolean loaded
  private List users = []

  void reset() {
    configDir = null
    loaded = false
    users = []
  }
  
  void init(String configDir) {
    this.configDir = configDir
    load()
  }

  private synchronized void load() {
    if (!loaded) {
      ConfigManager.instance.read(configDir as String, this.class.simpleName).each { k,v ->
        users << new User([id:k, name:v.name, profile:v.profile, aliases:v.aliases])
      }

      loaded = true
    }
  }

  public List<User> all() {
    UserManager.instance.users
  }

  public User getById(String id) {
    users.find { u -> u.id == id }
  }

  public User find(String alias) {
    users.find { u -> u.getAliases().find { a -> a == alias }}
  }

  public User find(String id, UserSource source) {
    source.find(id)
  }

  public synchronized void add(User user) {
    if (!user?.id) {
      log.warn("Not adding user with null id; user=${user}")
      return
    }

    def foundUser = getById(user?.id)

    if (foundUser) {
      users.remove(foundUser)
    }
    
    users << user
    save()
  }

  private def save() {
    def data = [:] as Map
    users.each { u -> 
      data[u.id] = [id: u.id, name: u?.name, profile: u.profile, aliases:u.aliases] 
    }
    
    ConfigManager.instance.write(configDir as String, this.class.simpleName, data)
  }
}

class User {
  public String id
  public String name
  public Map profile = [:]
  private Set aliases = []

  public Map toMap() {
    [id:id, name:name, profile:profile, aliases:getAliases()]
  }

  public void setAliases() {
    return
  }

  public void addAlias(String alias) {
    this.aliases << alias
  }

  public List getAliases() {
    this.aliases + ([this.name, '@' + this.name] ?: []) as List
  }

  @Override
  public String toString() {
    toMap().toString()
  }
}