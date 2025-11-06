def generate = { n ->
  def chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  new Random().with {
    (1..n).collect { chars[nextInt(chars.size())] }.join()
  }
}

def prefix = memory['prefix'] = memory['prefix'] ?: 'int-'
if (prefix == "none") {
  memory['value'] = generate((int)memory['length'])
}
else {
  memory['value'] = prefix + generate((int)memory['length'])
}
