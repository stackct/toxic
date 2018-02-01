package toxic.user

import toxic.job.JobManager
import toxic.job.ConfigManager

import org.junit.*

class UserManagerTest {

  def savedData

  @Before
  public void before() {
    UserManager.instance.reset()
    
    savedData = [:]
    savedData['UserManager'] = ['W9123141': [id:'W9123141', name:'fred.flintstone', profile:[:], aliases:['fred']]]

    ConfigManager.instance.metaClass.read  = { String configDir, String name -> savedData[name] }
    ConfigManager.instance.metaClass.write = { String configDir, String name, Map data -> savedData[name] = data }

    UserManager.instance.init("/dev/null")
  }

  @After
  public void after() {
    UserManager.instance.reset()
  }

  @Test
  public void should_return_all_users() {
    def users = UserManager.instance.all()

    assert users.size() == 1
    assert users.every { u -> u instanceof User }
  }

  @Test
  public void should_get_user_by_id() {
    def fred = UserManager.instance.getById('W9123141')

    assert fred instanceof User
    assert fred.id == 'W9123141'
  }

  @Test
  public void should_find_user_by_name() {

    ['fred.flintstone', '@fred.flintstone'].each { username ->
      def fred = UserManager.instance.find(username)
      assert fred instanceof User
      assert fred.id == 'W9123141'
    }
  }

  @Test
  public void should_find_user_by_alias() {
    def fred = UserManager.instance.find('fred')

    assert fred instanceof User
    assert fred.id == 'W9123141'
  }

  @Test
  public void should_render_user_as_map() {
    assert UserManager.instance.find('fred.flintstone').toMap() == [id:'W9123141', name:'fred.flintstone', profile:[:], aliases:['fred', 'fred.flintstone', '@fred.flintstone']]
  }

  @Test
  public void should_update_user_if_already_exists() {
    UserManager.instance.find('fred.flintstone').with { fred ->
      fred.addAlias('ff')
      UserManager.instance.add(fred)
      assert UserManager.instance.find('ff').id == 'W9123141'
    }
  }

  @Test
  public void should_add_user_if_not_exists() {
    assert !UserManager.instance.find('betty.rubble')
    
    def betty = new User(id:'W3234235')
    betty.addAlias('betty.rubble')
    UserManager.instance.add(betty)
    assert UserManager.instance.find('betty.rubble')
  }

  @Test
  public void should_not_add_user_with_null_id() {
    def betty = new User(id:null, name:'betty.rubble')

    UserManager.instance.add(betty)
    assert !UserManager.instance.find('betty.rubble')
  }

  @Test
  public void should_save_user_when_added() {
    def betty = new User(id:'W567890', name: 'Betty Rubble', profile:[:], aliases:['betty'])

    UserManager.instance.add(betty)

    assert savedData['UserManager']['W567890'] == [id:'W567890', name:'Betty Rubble', profile:[:], aliases:['betty', 'Betty Rubble', '@Betty Rubble']]
  }
}
