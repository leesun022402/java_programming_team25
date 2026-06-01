package halligalli.net;

/**
 * Text protocol constants between server and client. Messages are line-based
 * (newline-delimited) UTF-8.
 *
 * <p>Client -> server: {@code JOIN <name>}, {@code FLIP}, {@code BELL}, {@code QUIT}</p>
 * <p>Server -> client: {@code WELCOME}, {@code INFO}, {@code START}, {@code EVENT},
 * {@code STATE ...}, {@code ERROR}, {@code GAMEOVER <name>}</p>
 *
 * <p>STATE format (parseable):</p>
 * <pre>STATE|turn=P1|bellable=false|house=3|chipsym=0|fruits=BANANA:3,LIME:1|p=P1,12,0,active|p=P2,...</pre>
 */
public final class Protocol {

    public static final int DEFAULT_PORT = 5555;

    // client -> server
    public static final String CMD_JOIN = "JOIN";
    public static final String CMD_FLIP = "FLIP";
    public static final String CMD_BELL = "BELL";
    public static final String CMD_QUIT = "QUIT";

    // server -> client
    public static final String MSG_WELCOME = "WELCOME";
    public static final String MSG_INFO = "INFO";
    public static final String MSG_START = "START";
    public static final String MSG_EVENT = "EVENT";
    public static final String MSG_STATE = "STATE";
    public static final String MSG_ERROR = "ERROR";
    public static final String MSG_GAMEOVER = "GAMEOVER";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_OUT = "out";

    private Protocol() {
    }
}
