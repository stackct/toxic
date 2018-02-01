package toxic.job

import org.junit.*

public class EventManagerTest {
  def testDir = "/tmp/EventManagerTest"
  def date
  
  @Before
  public void before() {
    date = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-12-05 07:16:12.123")
    EventManager.metaClass.getNow = { -> return date }
  }
  
  @After
  public void after() {
    EventManager.instance.destroy()
    EventManager.metaClass = null
  }
  
  @Test (expected=NullPointerException) 
  public void shouldRequireNonNullEventDir() {
    EventManager.instance.init(null)
  }
  
  @Test (expected=IllegalStateException)
  public void shouldRequireInit() {
    assert !EventManager.instance.isInitialized()
    EventManager.instance.createEvent("test")
  }
  
  @Test
  public void shouldCreateAndFindEvent() {
    EventManager.instance.init(testDir)
    assert EventManager.instance.isInitialized()
    EventManager.instance.createEvent("test")
    def event = EventManager.instance.findEvent("test")
    assert event.time == date.time
  }

  @Test
  public void shouldCreateAndFindEventData() {
    EventManager.instance.init(testDir)
    EventManager.instance.createEvent("test", [junk:12])
    def event = EventManager.instance.findEvent("test")
    assert event.time == date.time
    assert event.junk == 12
  }

  @Test
  public void shouldPrepareAndReleaseEvents() {
    EventManager.instance.init(testDir)
    EventManager.instance.prepareEvent("test1-reminder", [junk:1])
    EventManager.instance.prepareEvent("test2-reminder", [junk:2])
    EventManager.instance.createEvent("test3", [junk:3])

    def event = EventManager.instance.findEvent("test1-reminder.pending")
    assert event.time == date.time
    assert event.junk == 1

    event = EventManager.instance.findEvent("test2-reminder.pending")
    assert event.time == date.time
    assert event.junk == 2

    event = EventManager.instance.findEvent("test3")
    assert event.time == date.time
    assert event.junk == 3

    // Release events up to date-1 age (should not be any eligible)
    EventManager.instance.releaseEvents(/.*-reminder/, date - 1)

    event = EventManager.instance.findEvent("test1-reminder.pending")
    assert event.time == date.time
    assert event.junk == 1

    event = EventManager.instance.findEvent("test2-reminder.pending")
    assert event.time == date.time
    assert event.junk == 2

    // Release evens up to date + 1 age (should only match the test1 and test2 events)
    EventManager.instance.releaseEvents(/.*-reminder/, date + 1)

    assert !EventManager.instance.findEvent("test1-reminder.pending")
    event = EventManager.instance.findEvent("test1-reminder")
    assert event.time == date.time
    assert event.junk == 1

    assert !EventManager.instance.findEvent("test2-reminder.pending")
    event = EventManager.instance.findEvent("test2-reminder")
    assert event.time == date.time
    assert event.junk == 2

    event = EventManager.instance.findEvent("test3")
    assert event.time == date.time
    assert event.junk == 3
  }
}