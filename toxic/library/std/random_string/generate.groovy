def generate = { n ->
  def chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  new Random().with {
    (1..n).collect { chars[nextInt(chars.size())] }.join()
  }
}

memory['prefix'] = memory['prefix'] ?: 'int-'
memory['value'] = memory['prefix'] + generate((int)memory['length'])
