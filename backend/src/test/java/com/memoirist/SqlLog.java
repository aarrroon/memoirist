package com.memoirist;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Locale;
import org.slf4j.LoggerFactory;

/**
 * Records the SQL that Hibernate runs while it is open, so a test can assert on the statements themselves rather than
 * on what the code looks like it should do.
 *
 * <pre>{@code
 * try (SqlLog sql = SqlLog.capture()) {
 *     repository.save(entry);
 *     entityManager.flush();
 *     assertThat(sql.statements()).hasSize(1);
 * }
 * }</pre>
 *
 * <p>Hibernate logs every statement to the {@code org.hibernate.SQL} logger at DEBUG. This turns that logger up and
 * attaches a list to it for the duration of the {@code try} block, then puts both back. It changes no Spring
 * configuration, so the test keeps sharing its cached application context (and its Postgres container) with every
 * other {@link IntegrationTest}. Bound parameters are not logged: a statement shows {@code where e1_0.id=?}, which is
 * all these assertions need.
 *
 * <p>Statements are recorded in the order they ran, lower-cased and on one line, so tests match on lower-case
 * fragments such as {@code "insert into entries"}.
 */
public final class SqlLog implements AutoCloseable {

    private final Logger logger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level previousLevel = logger.getLevel();
    private final boolean previousAdditive = logger.isAdditive();

    private SqlLog() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        // Keep the statements out of the console: the test reads them from the list.
        logger.setAdditive(false);
    }

    /** Starts recording. Use it in a try-with-resources block so the logger is always put back. */
    public static SqlLog capture() {
        return new SqlLog();
    }

    /** Every statement so far, in the order it ran. */
    public List<String> statements() {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ")
                        .strip())
                .toList();
    }

    /** The statements that start with this text, such as {@code "select"} or {@code "insert into entries"}. */
    public List<String> statementsStartingWith(String prefix) {
        return statements().stream().filter(sql -> sql.startsWith(prefix)).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditive);
        appender.stop();
    }
}
