package de.art.jarvis;

import org.junit.Test;
import static org.junit.Assert.*;

/** Strictly offline: no paid cloud calls. */
public class OpenAiBudgetTest {
    @Test public void stopsAllRequestsAtLimitIncludingExplicitQuestions() {
        assertTrue(OpenAiBudget.allowed(0));
        assertTrue(OpenAiBudget.allowed(OpenAiBudget.DAILY_LIMIT - 1));
        assertFalse(OpenAiBudget.allowed(OpenAiBudget.DAILY_LIMIT));
        assertFalse(OpenAiBudget.allowed(OpenAiBudget.DAILY_LIMIT + 1));
        assertFalse(OpenAiBudget.allowed(-1));
    }
}
