package halligalli.exception;

/** Thrown when an action references a player who does not belong to the game. */
public class InvalidPlayerException extends HalliGalliException {
    public InvalidPlayerException(String message) {
        super(message);
    }
}
