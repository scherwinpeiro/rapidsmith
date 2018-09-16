package edu.byu.ece.rapidSmith.util;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Jakob on 14.07.2015.
 */
public class SetHelpers {
    public static <A,B> Map<A,Set<B>> group(Collection<B> c, Function<B,A> attr) {
        Map<A,Set<B>> result = new HashMap<>();

        for (B b : c) {
            A val = attr.apply(b);

            Set<B> set = result.get(val);
            if (set==null) {
                set=new HashSet<>();
                result.put(val,set);
            }
            set.add(b);
        }
        return result;
    }
    public static <A,B> Map<A,Set<B>> multigroup(Collection<B> c, Function<B,Collection<A>> attr) {
        return multigroup(c,attr,x->x);
    }
    public static <A,B,C> Map<A,Set<B>> multigroup(Collection<B> c, Function<B,Collection<C>> attr, Function<C,A> converter) {
        Map<A,Set<B>> result = new HashMap<>();

        for (B b : c) {
            Collection<C> vals = attr.apply(b);
            for (C cval : vals) {
                A val =converter.apply(cval);
                Set<B> set = result.get(val);
                if (set==null) {
                    set=new HashSet<>();
                    result.put(val,set);
                }
                set.add(b);
            }

        }
        return result;
    }

    public static <A,B> void putIntoSet(Map<A,Set<B>> map, A key,  B value) {
		map.computeIfAbsent(key, k -> new HashSet<>()).add(value);
    }


    private static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }



    public static String dumpVs(String a, String b) {
        String[] aArr = a.split("\n");
        String[] bArr = b.split("\n");

        return dumpVs(aArr, bArr);
    }

    public static String dumpVs(String[] aArr, String[] bArr) {
        int aMaxLen = Arrays.stream(aArr).map(String::length).max(Comparator.<Integer>naturalOrder()).orElse(0);


        return IntStream.range(
                0, Math.max(aArr.length, bArr.length))
                .mapToObj(i -> {
                    String currA = (i < aArr.length) ? aArr[i] : "";
                    String currB = (i < bArr.length) ? bArr[i] : "";
                    return padRight(currA, aMaxLen) + " | " + currB;
                }).collect(Collectors.joining("\n"));
    }
}
