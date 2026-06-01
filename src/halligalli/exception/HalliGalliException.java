package halligalli.exception;

/** Root exception for the Halli Galli game domain. */
public class HalliGalliException extends RuntimeException {
    public HalliGalliException(String message) {
        super(message);
    }
}
