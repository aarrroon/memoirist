package com.memoirist;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Records every log line from every logger, at the most detailed level (TRACE), while it is open, so a test can prove
 * that some text appears in <em>no</em> log line, however verbose the logging is turned up. That is the strongest form
 * of "never logged": a library that logs the URL it calls at DEBUG would show up here.
 *
 * <pre>{@code
 * try (EverythingLogged log = EverythingLogged.capture()) {
 *     service.search("my private words");
 *     assertThat(log.text()).doesNotContain("my private words");
 * }
 * }</pre>
 *
 * <p>It turns the root logger up and attaches a list to it, then puts both back. A line counts as its message, the
 * arguments already put in, and the whole exception with its causes (class, message and stack frames), because an
 * exception's message is where a library hides the URL it failed on. Other tests in the same JVM that log while it
 * is open add lines to the list too, which can only make a "does not contain" check stricter.
 */
public final class EverythingLogged implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level previousLevel = root.getLevel();

    private EverythingLogged() {
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.TRACE);
    }

    public static EverythingLogged capture() {
        return new EverythingLogged();
    }

    /** One entry per log event: its logger, message and exception, as the text a log file would hold. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : List.copyOf(appender.list)) {
            StringBuilder line = new StringBuilder(event.getLoggerName())
                    .append(' ')
                    .append(event.getLevel())
                    .append(' ')
                    .append(event.getFormattedMessage());
            for (IThrowableProxy throwable = event.getThrowableProxy();
                    throwable != null;
                    throwable = throwable.getCause()) {
                line.append('\n').append(throwable.getClassName()).append(": ").append(throwable.getMessage());
                for (StackTraceElementProxy frame : throwable.getStackTraceElementProxyArray()) {
                    line.append('\n').append(frame.getSTEAsString());
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Every line joined, for {@code doesNotContain}. */
    public String text() {
        return String.join("\n", lines());
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        root.setLevel(previousLevel);
        appender.stop();
    }
}
