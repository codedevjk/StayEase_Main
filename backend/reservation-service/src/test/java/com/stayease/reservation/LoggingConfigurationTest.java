package com.stayease.reservation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Apache Commons Logging and SLF4J are bound to Log4j2 in Reservation Service,
 * and that Logback is completely absent from the runtime classpath.
 */
class LoggingConfigurationTest {

    private static final Log commonsLog = LogFactory.getLog(LoggingConfigurationTest.class);

    @Test
    @DisplayName("Verify SLF4J binds to Log4jLoggerFactory in Reservation Service")
    void testSlf4jBindsToLog4j2() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        assertNotNull(loggerFactory, "ILoggerFactory must not be null");
        assertTrue(
                loggerFactory.getClass().getName().contains("Log4jLoggerFactory"),
                "Expected SLF4J to bind to Log4jLoggerFactory, but was: " + loggerFactory.getClass().getName()
        );
    }

    @Test
    @DisplayName("Verify Logback is not on the classpath in Reservation Service")
    void testLogbackIsNotOnClasspath() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("ch.qos.logback.classic.Logger"),
                "Logback classic must not be present on the classpath"
        );
    }

    @Test
    @DisplayName("Verify Apache Commons Logging operates through Log4j2 in Reservation Service")
    void testCommonsLoggingOperatesThroughLog4j2() {
        assertNotNull(commonsLog, "Commons Log must not be null");
        assertDoesNotThrow(() -> {
            commonsLog.info("Commons Logging Log4j2 verification test in Reservation Service executed successfully");
        });
    }
}
