package util

import groovy.mock.interceptor.StubFor
import groovy.time.TimeCategory

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class DateTime {
  static now() {
    new Date()
  }

  // TODO ES: you cant default the format that then try to auto-sense it. It screws up randomly on time lengths.
  static parse(time, format = "yyyy-MM-dd HH:mm:ss") {
    time = time.replaceAll("T", " ")

    if (time.size() == "yyyy-MM-ddTHH:mm:ss.SSSSSSSzzz:zz".size() ) {
      time = time.replaceAll(/(\d\d\d)\d\d\d\d(.\d\d):(\d\d)/, '$1 $2$3')
      format = "yyyy-MM-dd HH:mm:ss.SSS zzzzz"
    } 
    else if (time.size() == "yyyy-MM-dd HH:mm:ss.SSS".size() ) {
      format = "yyyy-MM-dd HH:mm:ss.SSS"
    }
    else if (time.size() == "yyyy-MM-dd".size() ) {
      format = "yyyy-MM-dd"
    }

    new SimpleDateFormat(format).parse(time)
  }

  /**
   *
   * @exception java.text.ParseException when time is unparseable
   */
  static parseZulu(time, format = "yyyy-MM-dd'T'HH:mm:ss'Z'") {
    def df = new SimpleDateFormat(format)
    df.setTimeZone(TimeZone.getTimeZone("Zulu"))
    df.parse(time)
  }

  static mock(Date ...date) {
    createMock(date)
  }

  static mock(String ...date) {
    def dates = date.collect { parse(it) }
    createMock(*dates)
  }

  private static createMock(...date) {
    def schedulerMock = new StubFor(DateTime)
    schedulerMock.ignore.parse()
    schedulerMock.ignore.formatHHMMSS()
    schedulerMock.ignore.format()
    schedulerMock.ignore.fromExpDate()
    date.each { nextDate ->
      schedulerMock.demand.now(1) { nextDate }
    }
    schedulerMock.demand.now(99) { date[-1] }

    schedulerMock
  }

  static sleepMilliseconds(pauseMilliseconds) {
    TimeUnit.MILLISECONDS.sleep(pauseMilliseconds)
  }

  static isValid(String format, String date) {
    if (date?.size() != format?.size()) return false

    def valid
    try {
      def dtFormat = new SimpleDateFormat(format)
      dtFormat.lenient = false
      dtFormat.parse(date)
      valid = true
    }
    catch (java.text.ParseException e) {
      valid = false
    }

    return valid
  }

  static lastMillisecondOfDay(String day) {
    if (!day) throw new NullPointerException("day argument cannot be null when calculating last millisecond of a day")
    def cal = (parse(day) + 1).toCalendar()
    cal.add(Calendar.MILLISECOND, -1)
    return cal.time
  }

  static Date fromExpDate(String expDate) {
    if (expDate.size() != "MMYYYY".size())
      throw new IllegalArgumentException("expDate not in correct format (MMYYYY)")

    def mo = expDate[0..1]
    def yr = expDate[2..5]
    def cal = new GregorianCalendar(new Integer(yr), new Integer(mo)-1, 1)

    use (TimeCategory) { return cal.time + 1.month - 1.millisecond }
  }

  static boolean isExpired(Date date, validForMillis) {
    def expiredCal = date?.toCalendar()
    expiredCal?.add(Calendar.MILLISECOND, validForMillis)
    return now() > expiredCal?.time
  }

  static boolean isPast(Date date) {
    return now() > date
  }
  
  static daysToSeconds(days) {
    days * 24 * 60 * 60
  }
  
  static yearsToSeconds(years) {
    daysToSeconds(years * 365)
  }
  
  static secondsBetweenDates(date1, date2) {
    (date1.time - date2.time).abs() / 1000
  }

  static format(date, format = 'yyyy-MM-dd HH:mm:ss.SSS') {
    date ? new SimpleDateFormat(format).format( date ) : ""
  }

  static formatHHMMSS(seconds) {
    long hh, mm, ss = 0
    def negative = (seconds && seconds < 0)

    if (seconds) {
      hh = (seconds / 3600)
      mm = (seconds % 3600) / 60
      ss = (seconds % 3600) % 60

      if (negative) {
        hh = -hh
        mm = -mm
        ss = -ss
      }
    }

    sprintf("%s%02d:%02d:%02d", (negative ? "-":""), hh, mm ,ss)
  }
}

