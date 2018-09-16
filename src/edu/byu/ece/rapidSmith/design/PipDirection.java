package edu.byu.ece.rapidSmith.design;

/**
 * Created by wenzel on 03.11.16.
 */
public enum PipDirection {
    /**
     * Only forward, buffered
     */
    DIRECTIONAL("->", false),
    /**
     * Bidirectional, unbuffered
     */
    BI_UNBUFFERED("==", true),
    /**
     * Bidirectional, buffered in one direction
     */
    BI_BUFONE("=>", true),
    /**
     * Bidirectional, buffered in both directions
     */
    BI_BUFBOTH("=-", true);

    public final String connString;
    public final boolean isBidirectional;

    PipDirection(String connString, boolean isBidirectional) {
        this.connString = connString;
        this.isBidirectional = isBidirectional;
    }

    public static PipDirection fromStr(String str) {
        for (PipDirection pipDirection : values()) {
            if (pipDirection.connString.equals(str))
                return pipDirection;
        }
        return null;
    }

    @Override
    public String toString() {
        return connString;
    }
}
