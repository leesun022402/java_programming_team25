package halligalli.exception;

/** Thrown when an action is attempted after the game has ended. */
public class GameOverException extends HalliGalliException {
    public GameOverException(String message) {
        super(message);
    }
}
