package dev.meguviz.drawable.core;

import dev.meguviz.drawable.model.StateSpec;
import dev.meguviz.drawable.model.StateColorList;

/** Direct state matching; no assumptions are made about the number or magnitude of IDs. */
public final class StateMatcher {
    private StateMatcher() {}

    public static boolean matches(StateSpec spec, int[] state) {
        if (spec == null || state == null) {
            throw new NullPointerException("spec and state are required");
        }
        for (int i = 0; i < spec.size(); i++) {
            int condition = spec.conditionAt(i);
            if (condition == 0) {
                break;
            }
            int id = condition > 0 ? condition : -condition;
            boolean present = false;
            for (int entry : state) {
                if (entry == 0) {
                    break;
                }
                if (entry == id) {
                    present = true;
                    break;
                }
            }
            if (present != (condition > 0)) {
                return false;
            }
        }
        return true;
    }

    /** Returns the first matching rule index, or -1 when none matches. */
    public static int matchIndex(StateColorList rules, int[] state) {
        if (rules == null || state == null) {
            throw new NullPointerException("rules and state are required");
        }
        for (int i = 0; i < rules.size(); i++) {
            if (matches(rules.specAt(i), state)) {
                return i;
            }
        }
        return -1;
    }
}
