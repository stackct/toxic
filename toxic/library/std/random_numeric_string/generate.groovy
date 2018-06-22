def generate = { n ->
  def chars = '0123456789'
  new Random().with {
    (1..n).collect { chars[nextInt(chars.size())] }.join()
  }
}

memory['prefix'] = memory['prefix'] ?: ''
memory['value'] = memory['prefix'] + generate((int)memory['length'])
