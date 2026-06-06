package halligalli.exception;

/** Thrown when a player tries to spend a chip they do not have. */
public class NoChipException extends HalliGalliException {
    public NoChipException(String message) {
        super(message);
    }
}
