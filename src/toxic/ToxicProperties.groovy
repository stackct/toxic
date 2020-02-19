
package toxic

import com.google.common.collect.EvictingQueue
import com.google.common.collect.Lists
import com.google.common.collect.Queues
import toxic.groovy.GroovyEvaluator
import org.apache.log4j.Logger

/**
 * Extends the simple Properties class by ensure any returned value
 * is first resolved by the GroovyEvaluator class.
 */
public class ToxicProperties extends ConfigObject {
  protected static Logger slog = Logger.getLogger(ToxicProperties.class.name)

  private static def classCache = [:]
  private backupStack = []

  private static final int MOST_RECENT_COUNT = 10
  private Queue<Object> lastModifiedKeys = Queues.synchronizedQueue(EvictingQueue.create(MOST_RECENT_COUNT))

  /**
   * Key prefix used to indicate 'secure' properties.
   * Secure properties will not be included in toString output.
   */
  public static final String SECURE_PREFIX = "secure."

  public ToxicProperties() {
  }

  public ToxicProperties(Properties props) {
    super()
    putAll(props)
  }

  public ToxicProperties(ToxicProperties props) {
    super()
    putAll(props)
  }

  protected Logger getLog() {
    return getRaw("log") ?: this.slog
  }

  public String toString() {
    def props = new LinkedHashMap(MOST_RECENT_COUNT)
    def keys = lastModifiedKeys
    Lists.reverse(keys.asList()).each {
      props[it] = getMaskedValue(it)
    }
    def id = getClass().getName() + "@" + Integer.toHexString(hashCode())
    return "${id} - Last ${lastModifiedKeys.size()} modified properties: ${props.toString()}"
  }

  public String getMaskedValue(String key) {
    return key.startsWith(SECURE_PREFIX) ? "***" : getRaw(key).toString()
  }

  public String toStringProperties() {
    def newProps = ""
    entrySet().each {
      newProps += "${it.key}=${getMaskedValue(it.key)}\n"
    }
    return newProps
  }

  public void putAll(ToxicProperties props) {
    props.entrySet().each {
      def val = it.valueRaw
      if (!(val instanceof ConfigObject && !val)) {
        put(it.key, val)
      }
    }
  }

  public ConfigObject clone() {
    def props = new ToxicProperties()
    props.putAll(this)
    return props
  }

  public Set<Map.Entry<Object, Object>> entrySet() {
    def s = new HashSet<Map.Entry<Object, Object>>()
    def hashSet = super.entrySet()
    hashSet.each {
      s << new Entry(it.key, it.value, this)
    }
    return s
  }

  public Object put(Object key, Object value) {
    // If the key string ends with ++, append a count
    // to the end of the key name, where the count denotes the number
    // of keys with this name that have been put in this map.  Ex: foo++
    // will assign the first value to "foo0".  A second put against foo++
    // will assign the second value to "foo1".  foo_count will contain the
    // total count of "foo<#>" keys that have been put.
    if (isString(key) && (key.endsWith("++"))) {
      key = key[0..-3]
      def countKeyName = key + "_count"
      long count = containsKey(countKeyName) ? getRaw(countKeyName) : 0
      key += "$count"
      super.put(countKeyName, ++count)
    }

    // If the value starts with `` and ends with `` then evaluate the embedded
    // groovy script immediately.
    if (isString(value) && value.contains("``")) {
      value = GroovyEvaluator.resolve(value, this, '``')
    }

    lastModifiedKeys.add(key)
    return super.put(key, value)
  }

  public Object get(Object key) {
    def value
    if (isString(key) && key.startsWith("+") && containsKey(key[1..-1] + "_count")) {
      // Sum up the key values that contain a numeric suffix. Ex: +foo will
      // add up foo0 + foo1 + fooN, where N = foo_count.  This logic corresponds
      // to the ToxicProperties.put logic that accepts keys ending with ++.
      // This is only valid on numeric values.  Other value types will throw an
      // exception.
      def count = getRaw(key[1..-1] + "_count")
      value = 0
      count.times {
        value += getRaw(key[1..-1] + it.toString()).toBigDecimal()
      }
    } else if (isString(key) && key.startsWith("!") && containsKey(key[1..-1] + "_count")) {
      // Fetch the most recently saved variable of the given variable set.
      def count = getRaw(key[1..-1] + "_count")
      value = getRaw(key[1..-1] + (count - 1))
    } else {
      value = getRaw(key)
      if (isString(value)) {
        value = GroovyEvaluator.resolve(value, this)
      }
    }
    return value
  }

  public Object getRaw(Object key) {
    return super.get(key)
  }

  public Object remove(Object key) {
    def value = get(key)
    super.remove(key)
    return value
  }

  public Collection<Object> values() {
    def vs = super.values()
    def result = []
    vs.each {
      def value = it
      if (isString(value)) {
        value = GroovyEvaluator.resolve(value, this)
      }
      result << value
    }
    return result
  }

  public Class resolveClass(def key) {
    Class c
    def className = get(key)
    if (className != null) {
      c = classCache[className]
      if (c == null) {
        synchronized (classCache) {
          // Repeat cache check since we now synchronized.
          c = classCache[className]
          if (c == null) {
            c = Class.forName(className)
            classCache[className] = c
          }
        }
      }
    }
    return c
  }

  public void forProperties(String propPrefix, Closure c) {
    def keys = [] as Set
    synchronized(this) {
      keys.addAll(keySet())
    }
    keys?.findAll {
      it.toString().startsWith(propPrefix)
    }.sort()?.each { key ->
      c(key, get(key))
    }
  }

  public void load(InputStream is) {
    load(new StringReader(is.text))
  }

  public void load(Reader reader) {
    // Load the properties using the tried-and-true Properties
    // loader since it has support for escaped characters, unicode,
    // etc.
    Properties p = new Properties()
    p.load(reader)

    // Ensure all props are put() into this ToxicProperties
    // object in the order in which they are reflected in
    // the underlying reader.  This is critical to ensuring
    // embedded groovy scripts are evaluated in proper order.
    reader.reset()
    def br = new BufferedReader(reader)
    def line = br.readLine()
    while (line != null) {
      if (!line.trim().startsWith("#") && !line.trim().startsWith("//") && line.contains("=")) {
        def key = line.split("=")[0].trim()
        def value = p[key]
        put(key, value)
        if (getLog().isDebugEnabled()) {
          getLog().debug("Loading property; key=${key}; value=${value}; resolvedValue=${get(key)}")
        }
        p.remove(key)
      }
      line = br.readLine()
    }

    // Now add any remaining properties that somehow slipped through
    // and log a warning.
    if (p) {
      getLog().warn("Encountered unmatched properties; unmatched=" + p)
      putAll(p)
    }
  }

  public static boolean isString(def s) {
    return (s instanceof String) || (s instanceof GString)
  }

  public boolean isTrue(def key) {
    return "true".equalsIgnoreCase(get(key).toString())
  }

  public boolean isNothing(def key) {
    return get(key) == null || get(key) == [:]
  }

  public void push() {
    backupStack.push(clone())
  }

  public void pop() {
    if (backupStack.size() > 0) {
      clear()
      putAll(backupStack.pop())
    }
  }

  public int stackSize() {
    return backupStack.size()
  }

  public static class Entry implements Map.Entry<Object, Object> {
    private Object key
    private Object value
    private ToxicProperties props

    public Entry(Object key, Object value, ToxicProperties props) {
      this.key = key
      this.value = value
      this.props = props
    }

    public boolean equals(Object o) {
      return (o?.key == key) && (o?.value == value)
    }

    public Object getKey() {
      return key
    }

    public Object getValue() {
      if (isString(value)) {
        value = GroovyEvaluator.resolve(value, props)
      }
      return value
    }

    public Object getValueRaw() {
      return value
    }

    public int hashCode() {
      return key.hashCode()
    }

    public Object setValue(Object value) {
      this.value = value
    }
  }
}
