package de.art.jarvis;

/** Deterministic daily request-count safety brake, not a claim about currency spent.
 * Failed calls count as attempts. No live API is needed to test this helper.
 */
final class OpenAiBudget {
    static final int DAILY_LIMIT = 120;
    private OpenAiBudget() { }
    static boolean allowed(int usedToday) { return usedToday >= 0 && usedToday < DAILY_LIMIT; }
}
