package halligalli.core;

/** Classification of a bell-ring result. */
public enum RingOutcome {
    /** Fruit total of 5 met -> win the face-up cards. */
    FRUIT,
    /** 3 chip symbols met -> win 1 chip and reset the table cards. */
    CHIP,
    /** Both fruit and chip met -> win cards + chip. */
    BOTH,
    /** Conditions not met (false bell) -> penalty. */
    INVALID
}
