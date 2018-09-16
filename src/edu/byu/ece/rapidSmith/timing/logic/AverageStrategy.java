package edu.byu.ece.rapidSmith.timing.logic;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by jakobw on 03.07.15.
 */
public enum AverageStrategy {
    ARITHMETIC_MEAN(floats -> {
        double sum = floats.stream().collect(Collectors.summingDouble(f -> f));
        return (float)sum / floats.size();
    }),
    MEDIAN(floats -> {
        List<Float> l = new ArrayList<>(floats);
        Collections.sort(l);
        if (l.size() % 2 != 0)
            return l.get(l.size() / 2);
        else {
            int half = l.size() / 2;
            float a = l.get(half);
            float b = l.get(half - 1);
            return (a + b) / 2;
        }
    }),
    MAX(floats -> floats.stream().max(Comparator.<Float>naturalOrder()).get());

    public final Function<Collection<Float>, Float> strategy;

    AverageStrategy(Function<Collection<Float>, Float> strategy) {
        this.strategy = strategy;
    }
}
