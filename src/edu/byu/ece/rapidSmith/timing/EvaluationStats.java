package edu.byu.ece.rapidSmith.timing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Jakob on 17.08.2015.
 */
public class EvaluationStats {
    static class StatsBase {
        int knownCount;
        int unknownCount;

    }
    static class Stats extends StatsBase {
        double average;
        double variance;
    }
    static class StatsTemp extends StatsBase {
        double mean;
        double m2;
    }
    static class Data {
        double xilinx;
        double ours;
        double error;
    }
    public static void main(String[] args) {
        File dumps = new File("dumps/");
        String[] files = dumps.list((dir, name) -> {
            return name.contains(".dat");
        });
        Map<String, List<String>> byConfig = Stream.of(files).collect(
                Collectors.groupingBy((String x) -> {
                    int minusPos = x.indexOf('-');
                    return x.substring(minusPos+1,x.length()-4);
                }));
        byConfig.keySet().stream().filter(x -> x.endsWith("logicSingle")).forEach(config -> {
            List<String> values = byConfig.get(config);
            Stats stats = values.stream().map(s -> {
                try {
                    return new BufferedReader(new FileReader(new File("dumps/" + s)));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            }).flatMap(BufferedReader::lines).map(x -> {
                Data res = new Data();
                String[] parts = x.split("\t");
                res.xilinx = Double.parseDouble(parts[0]);
                res.ours = Double.parseDouble(parts[1]);

                res.error = (res.ours-res.xilinx)/res.xilinx;
                return res;
            }).collect(new Collector<Data, StatsTemp, Stats>() {
                @Override
                public Supplier<StatsTemp> supplier() {
                    return StatsTemp::new;
                }

                @Override
                public BiConsumer<StatsTemp, Data> accumulator() {
                    return (stats, d) -> {
                        if (d.ours < 100000) {
                            stats.knownCount++;

                            double delta = d.error - stats.mean;
                            stats.mean = stats.mean + delta/stats.knownCount;
                            stats.m2 = stats.m2 + delta*(d.error - stats.mean);
                        } else {
                            stats.unknownCount++;
                        }
                    };
                }

                @Override
                public BinaryOperator<StatsTemp> combiner() {
                    return (a, b) -> {
                        /*a.knownCount += b.knownCount;
                        a.summedError += b.summedError;
                        a.sqrError += b.sqrError;
                        a.unknownCount += b.unknownCount;
                        return a;*/
                        throw new RuntimeException("not implemented");
                    };
                }

                @Override
                public Function<StatsTemp, Stats> finisher() {
                    return (temp) -> {
                        Stats res = new Stats();
                        res.knownCount = temp.knownCount;
                        res.unknownCount = temp.unknownCount;

                        res.average = temp.mean;
                        if (temp.knownCount>2)
                            res.variance = temp.m2/(temp.knownCount-1);
                        else res.variance = Double.NaN;

                        return res;
                    };
                }

                @Override
                public Set<Characteristics> characteristics() {
                    return Collections.emptySet();
                }
            });

            int minusPos = config.lastIndexOf('-');
            String name = config.substring(0,minusPos);
            System.out.println(name.charAt(0)+", "+name.charAt(1)+", "+name.charAt(2)+", "+name.substring(4)+", "+stats.average+", "+stats.variance+","+stats.knownCount+","+stats.unknownCount);

        });
    }
}
