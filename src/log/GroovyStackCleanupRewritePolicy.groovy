package log

import org.apache.log4j.rewrite.RewritePolicy
import org.apache.log4j.spi.LoggingEvent
import org.apache.log4j.spi.ThrowableInformation
import org.codehaus.groovy.runtime.StackTraceUtils

/**
 * An implementation of Log4j's RewritePolicy that removes groovy internals from logged stack traces.
 * Example usage (in log4j.xml):
 * <pre>
 *    <appender name="console" class="org.apache.log4j.rewrite.RewriteAppender">
 *      <rewritePolicy class="log.GoovyStackCleanupRewritePolicy" />
 *      <appender-ref ref="consoleTarget" />
 *    </appender>
 *    <appender name="consoleTarget" class="org.apache.log4j.ConsoleAppender">
 *      <layout class="org.apache.log4j.PatternLayout">
 *        <param name="ConversionPattern" value="%d{ISO8601}[%t] %-5p %c %x- %m%n"/>
 *      </layout>
 *     </appender>
 * </pre>
 */
class GroovyStackCleanupRewritePolicy implements RewritePolicy {

  public static final String[] REMOVE_LIST = ["at org.codehaus.groovy", "at groovy.lang.", "at sun.reflect.",
                                              "at java.lang.reflect." , "at com.sun.proxy." ]

  @Override
  LoggingEvent rewrite(LoggingEvent source) {
    ThrowableInformation throwableInfo = source.getThrowableInformation()
    if(throwableInfo) {
      throwableInfo = filterStackFrames(source, throwableInfo)
    }

    LoggingEvent filteredEvent = new LoggingEvent(source.getFQNOfLoggerClass(), source.getLogger(),
        source.getTimeStamp(), source.getLevel(), source.getMessage(), source.getThreadName(),
        throwableInfo, source.getNDC(), source.getLocationInformation(),
        source.getProperties())
    return filteredEvent
  }

  /**
   * Creates a new ThrowableInformation instance, removing any stack frames that contain text from
   * the remove list.
   */
  ThrowableInformation filterStackFrames(LoggingEvent source, ThrowableInformation throwableInfo) {
    def sanitized = StackTraceUtils.sanitize(throwableInfo.throwable)
    new ThrowableInformation(sanitized)
  }
}
