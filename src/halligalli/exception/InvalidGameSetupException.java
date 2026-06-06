package halligalli.exception;

/** Thrown when the game cannot be constructed from an invalid setup. */
public class InvalidGameSetupException extends HalliGalliException {
    public InvalidGameSetupException(String message) {
        super(message);
    }
}
