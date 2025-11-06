def domain = memory['domain'];
def length = (int)memory['length']

def generate = { n ->
  def chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  new Random().with {
    (1..n).collect { chars[nextInt(chars.size())] }.join()
  }
}

memory['address'] = generate(length) + '@' + domain