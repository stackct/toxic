package util

import org.junit.*

import java.text.ParseException

import static org.junit.Assert.*
import groovy.mock.interceptor.*
import org.junit.rules.ExpectedException

import java.text.SimpleDateFormat

class DateTimeTest {
  def defaultTz

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  void before() {
    defaultTz = TimeZone.getDefault()
    TimeZone.'default'= TimeZone.getTimeZone('GMT') //set the default time zone
  }

  @After
  void after() {
    TimeZone.setDefault(defaultTz)
  }
  
  @Test
  void should_return_the_time_now() {
    assert DateTime.now() instanceof Date
    assert DateTime.now()
  }
  
  @Test
  void should_produce_mock_that_injects_a_date() {
    def someDate = new Date()

    DateTime.mock(someDate).use {
      assert someDate == DateTime.now()
    }
  }
  
  @Test
  void should_produce_mock_that_injects_two_dates() {
    def date1 = new Date()
    def date2 = new Date()

    DateTime.mock(date1, date2).use {
      assert date1 == DateTime.now()
      assert date2 == DateTime.now()
    }
  }

  @Test
  void should_parse_zulu() {
    TimeZone.setDefault(TimeZone.getTimeZone('Zulu'))
    def cal = Calendar.instance
    cal.set(year: 2016, month: Calendar.MARCH, date: 03, hourOfDay: 15, minute: 38, second: 11)
    cal.set(Calendar.MILLISECOND, 000)

    assert cal.timeInMillis == DateTime.parseZulu('2016-03-03T15:38:11Z').time
  }

  @Test
  void should_parse_zulu_with_custom_format() {
    TimeZone.setDefault(TimeZone.getTimeZone('Zulu'))
    def cal = Calendar.instance
    cal.set(year: 2016, month: Calendar.MARCH, date: 03, hourOfDay: 15, minute: 38, second: 11)
    cal.set(Calendar.MILLISECOND, 000)

    assert cal.timeInMillis == DateTime.parseZulu('2016-03-03 15:38:11', 'yyyy-MM-dd HH:mm:ss').time
  }

  @Test(expected = ParseException)
  void should_throw_exception_when_parsing_invalid_zulu_time() {
    DateTime.parseZulu('NOT A DATE').time
  }
 
  @Test
  void should_be_used_to_produce_test_prescribed_dates_from_strings_yymmdd() {    
    def date1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1111-12-13 00:00:00")

    DateTime.mock("1111-12-13").use {
      assert date1 == DateTime.now()
    }
  }

  @Test
  void should_produce_two_mocks() {    
    def date1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1111-12-13 00:00:00")
    def date2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1111-12-14 00:00:00")

    DateTime.mock("1111-12-13", "1111-12-14").use {
      assert date1 == DateTime.now()
      assert date2 == DateTime.now()
    }
  }
  
  @Test
  void should_be_used_to_produce_test_prescribed_dates_from_strings_yymmdd_hhmmss() {
    def date1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1111-12-13 14:15:16")

    DateTime.mock("1111-12-13 14:15:16").use {
      assert date1 == DateTime.now()
    }
  }

  @Test
  void should_produce_mock_that_allows_fromExpDate() {
    DateTime.mock("1111-12-31 23:59:59.999").use {
      assert DateTime.now() == DateTime.fromExpDate("121111")
    }
  }

  @Test
  void should_format_the_date_time_string() {
    assert DateTime.parse("1111-12-13") == 
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1111-12-13 00:00:00")
    
    assert DateTime.parse("1111-12-13 14:15:16") == 
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1111-12-13 14:15:16")
      
    assert DateTime.parse("1111-12-13 14:15:16.017") ==
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse("1111-12-13 14:15:16.017")

    assert DateTime.parse("1111-12-13T01:02:03.4442221-04:00") == 
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzzzz").parse("1111-12-13 01:02:03.444 -0400")
  }

  /*
   * This is ungly and should be removed. Some risk in changing date time parsing
   * so holding on it.
   */
  @Test
  void should_throw_exception_if_datetime_string_is_invalid_WITH_MISSING_Ts() {
    exception.expect(java.text.ParseException);
    exception.expectMessage('Unparseable date: "INVALID_DA E IME"');

    DateTime.parse("INVALID_DATETIME")
  }

  @Test
  void should_sleep_for_a_number_of_milliseconds() {
    def waitTime = 1000
    def expectMax = waitTime + 2000

    def start = System.currentTimeMillis()
    DateTime.sleepMilliseconds(waitTime)
    def duration = System.currentTimeMillis() - start

    assert duration >= waitTime && duration < expectMax, "Duration ${duration} not in ${waitTime}..${expectMax}"
  }
  
  @Test
  void should_determine_if_string_is_date() {
    assert !DateTime.isValid('yyyy-MM-dd', '1111-11-11-11')
    assert !DateTime.isValid('yyyy-MM-dd', 'xx-11-11')
    assert DateTime.isValid('yyyy-MM-dd', '2012-12-25')
    assert !DateTime.isValid('yyyy-MM-dd HH:mm:ss', '2012-12-25 01:01:xx')
    assert !DateTime.isValid('yyyy-MM-dd HH:mm:ss', '2012-12-25 01:01:99')
    assert DateTime.isValid('yyyy-MM-dd HH:mm:ss', '2012-12-25 01:01:59')
    assert !DateTime.isValid('yyyy-MM-dd HH:mm:ss','2014-04-31 00:00:00')
    assert !DateTime.isValid('yyyy-MM-dd HH:mm:ss','2014-08-01 52:190:30')
  }
  
  @Test
  void should_determine_last_millisecond_of_a_day() {
    assert DateTime.lastMillisecondOfDay('2012-07-21') == Date.parse('yyyy-MM-dd HH:mm:ss.SSS', '2012-07-21 23:59:59.999')
    assert DateTime.lastMillisecondOfDay('2012-12-31') == Date.parse('yyyy-MM-dd HH:mm:ss.SSS', '2012-12-31 23:59:59.999')
  }

  @Test(expected=NullPointerException)
  void should_throw_npe_if_null_arg() {
    DateTime.lastMillisecondOfDay(null)
  }
  
  @Test
  void should_throw_exception_if_expDate_is_not_valid_format() {
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("expDate not in correct format (MMYYYY)");
    assert DateTime.fromExpDate("X") == DateTime.parse('2014-08-31 23:59:59.999')
  }
  
  @Test
  void should_create_exp_date_from_MMYYYY_format() {
    assert DateTime.fromExpDate("082014") == DateTime.parse('2014-08-31 23:59:59.999')
    assert DateTime.fromExpDate("122014") == DateTime.parse('2014-12-31 23:59:59.999')
    assert DateTime.fromExpDate("022013") == DateTime.parse('2013-02-28 23:59:59.999')
    assert DateTime.fromExpDate("022012") == DateTime.parse('2012-02-29 23:59:59.999')
  }

  @Test
  void should_test_expiration() {
    assert !DateTime.isExpired(DateTime.now(), 2000)
    assert DateTime.isExpired(DateTime.now() - 1, 2000)
    assert !DateTime.isExpired(DateTime.now() - 1, 2000 + (24 * 3600 * 1000))
    assert DateTime.isExpired(DateTime.now() - 1, -100 + (24 * 3600 * 1000))
  }

  @Test
  void should_test_date_is_in_the_past() {
    assert DateTime.isPast(DateTime.now() - 1)
    assert !DateTime.isPast(DateTime.now() + 1)
  }
  
  @Test
  void should_convert_days_to_seconds() {
    assert 15552000 == DateTime.daysToSeconds(180)
  }
  
  @Test
  void should_convert_years_to_seconds() {
    assert 157680000 == DateTime.yearsToSeconds(5)
  }
  
  @Test
  void should_calculate_seconds_between_dates() {
    def date1 = DateTime.parse('2015-01-01 00:00:00', 'yyyy-MM-dd HH:mm:ss')
    def date2 = DateTime.parse('2015-01-02 00:00:00', 'yyyy-MM-dd HH:mm:ss')
    
    assert 86400 == DateTime.secondsBetweenDates(date1, date2)
    assert 86400 == DateTime.secondsBetweenDates(date2, date1)
  }

  @Test
  void should_format_a_standard_datetime() {
    def date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse("1111-12-13 14:15:16.017")

    assert "1111-12-13 14:15:16.017"== DateTime.format(date)
  }

  @Test
  void should_format_a_custom_datetime() {
    def date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse("1111-12-13 14:15:16.017")

    assert "11111213141516"== DateTime.format(date, "yyyyMMddHHmmss")
  }

  @Test
  void should_format_seconds_into_hhmmss() {
    assert "01:02:03" == DateTime.formatHHMMSS(1 * 3600 + 2 * 60 + 3)
    assert "99:59:59" == DateTime.formatHHMMSS(99 * 3600 + 59 * 60 + 59)
    assert "100:59:59" == DateTime.formatHHMMSS(100 * 3600 + 59 * 60 + 59)

    assert "-01:02:03" == DateTime.formatHHMMSS(-(1 * 3600 + 2 * 60 + 3))
    assert "-00:00:01" == DateTime.formatHHMMSS(-1)
    
    assert "00:00:00" == DateTime.formatHHMMSS(null)
    assert "00:00:00" == DateTime.formatHHMMSS(0)
  }
}

