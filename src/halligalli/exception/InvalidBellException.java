package halligalli.exception;

/**
 * Thrown when a bell cannot be rung due to a rule violation (e.g. an eliminated player rings).
 * A plain false bell (conditions not met) is not an exception but a {@code RingOutcome.INVALID} result.
 */
public class InvalidBellException extends HalliGalliException {
    public InvalidBellException(String message) {
        super(message);
    }
}
