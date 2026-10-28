package io.github.jichuo1.meguviz.drawable.benchmark;

import io.github.jichuo1.meguviz.drawable.core.CornerRadii;
import io.github.jichuo1.meguviz.drawable.core.StateMatcher;
import io.github.jichuo1.meguviz.drawable.model.StateColorList;
import io.github.jichuo1.meguviz.drawable.model.StateSpec;
import java.util.ArrayList;
import java.util.List;

/** Exploratory host benchmark, not an Android/device performance test. */
public final class CorePilot {
    private static final int[] IDS = {101, 102, 103, 104, 105, 106, 107, 108};
    private static volatile long sink;
    private static final int WARMUP = 4;
    private static final int ROUNDS = 7;
    private static final int OPS = 1000000;

    public static void main(String[] args) {
        final int fork = Integer.parseInt(args[0]);
        List<StateSpec> specs = new ArrayList<StateSpec>();
        for (int i = 0; i < 12; i++) {
            specs.add(new StateSpec(IDS[i % 8], -IDS[(i + 3) % 8], IDS[(i + 5) % 8]));
        }
        specs.add(new StateSpec());
        int[] colors = new int[specs.size()];
        for (int i = 0; i < colors.length; i++) colors[i] = i;
        final StateColorList rules = new StateColorList(specs, colors);
        final int[][] states = new int[256][];
        final int[] table = new int[256];
        // Scramble mask order in measured loops; every mask occurs equally often.
        for (int mask = 0; mask < 256; mask++) {
            int[] values = new int[Integer.bitCount(mask) + 2];
            int count = 0;
            for (int b = 0; b < IDS.length; b++) if ((mask & (1 << b)) != 0) values[count++] = IDS[b];
            values[count] = 0;
            values[count + 1] = 999; // must be ignored after terminator
            states[mask] = values;
            table[mask] = StateMatcher.matchIndex(rules, values);
        }
        for (int mask = 0; mask < 256; mask++) {
            if (StateMatcher.matchIndex(rules, states[mask]) != lookup(table, states[mask])) {
                throw new AssertionError("fixture mismatch " + mask);
            }
        }
        final float[][] radii = {
                {12, 12, 12, 12, 12, 12, 12, 12},
                {90, 20, 80, 20, 12, 10, 14, 10},
                {120, 100, 110, 60, 130, 55, 80, 90},
                {4, 5, 0, 3, 13, 14, 15, 0}
        };
        System.out.println("meta,fork," + fork + ",java," + System.getProperty("java.version")
                + ",vm," + System.getProperty("java.vm.name") + ",arch," + System.getProperty("os.arch")
                + ",cores," + Runtime.getRuntime().availableProcessors());
        System.out.println("columns,fork,round,scenario,operations,elapsed_ns,checksum");
        for (int round = -WARMUP; round < ROUNDS; round++) {
            // Alternate candidate and baseline order between rounds and forks.
            if (((round + fork) & 1) == 0) {
                measure(fork, round, "state_direct", rules, states, table, radii);
                measure(fork, round, "state_lut8", rules, states, table, radii);
            } else {
                measure(fork, round, "state_lut8", rules, states, table, radii);
                measure(fork, round, "state_direct", rules, states, table, radii);
            }
            measure(fork, round, "corner_normalize", rules, states, table, radii);
        }
        System.out.println("sink," + sink);
    }

    private static void measure(int fork, int round, String scenario, StateColorList rules,
            int[][] states, int[] table, float[][] radii) {
        long sum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < OPS; i++) {
            int k = (i * 73 + 19) & 255;
            if (scenario.equals("state_direct")) sum += StateMatcher.matchIndex(rules, states[k]);
            else if (scenario.equals("state_lut8")) sum += lookup(table, states[k]);
            else {
                float[] result = CornerRadii.normalize(100, 80, radii[k & 3]);
                sum += Float.floatToRawIntBits(result[k & 7]);
            }
        }
        long elapsed = System.nanoTime() - start;
        sink ^= sum;
        if (round >= 0) System.out.println("sample," + fork + "," + round + "," + scenario + "," + OPS + "," + elapsed + "," + sum);
    }

    private static int lookup(int[] table, int[] states) {
        int mask = 0;
        for (int state : states) {
            if (state == 0) break;
            for (int bit = 0; bit < IDS.length; bit++) {
                if (state == IDS[bit]) { mask |= 1 << bit; break; }
            }
        }
        return table[mask];
    }
}
