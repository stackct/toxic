package toxic.job

import toxic.user.*

class UserManagerTestMixin {
  def mockUsers = []

  def userManagerSetup() {
    mockUsers << [id:'U1234567', name:'fred', profile:[email:'fred@home.com', image_24:'http://home.co/fred.jpg']]
    mockUsers << [id:'U1234568', name:'barney', profile:[email:'barney@home.com', image_24:'http://home.co/barnet.jpg']]
    mockUsers << [id:'U1234569', name:'wilma', profile:[email:'wilma@home.com', image_24:'http://home.co/wilma.jpg']]

    UserManager.instance.metaClass.getById = { String id ->
      mockUsers.find { u -> u.id == id }
    }

    UserManager.instance.metaClass.find = { String alias ->
      mockUsers.find { u -> u.name == alias }
    }

    UserManager.instance.metaClass.add = { User u ->
      if (u) {
        mockUsers << [id:u.id, name:u?.name, profile:u?.profile]
      }
    }
  }

  def userManagerTeardown() {
    UserManager.instance.metaClass = null
  }

  protected def user(String name) {
    mockUsers.find { it.name == name }?.id ?: 'UNKNOWN'
  }
}