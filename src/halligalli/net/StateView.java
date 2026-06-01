package halligalli.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A read-only view parsed from the server's STATE line. Shared by console/bot clients. */
public final class StateView {

    public final String turn;
    public final boolean bellable;
    public final int house;
    public final int chipSymbols;
    public final Map<String, Integer> fruits = new LinkedHashMap<>();
    public final List<PlayerInfo> players = new ArrayList<>();

    public static final class PlayerInfo {
        public final String name;
        public final int cards;
        public final int chips;
        public final boolean active;
        /** Visible (top) card, or null if none. */
        public final String visFruit; // enum name, e.g. "BANANA"
        public final int visCount;
        public final boolean visChip;

        PlayerInfo(String name, int cards, int chips, boolean active,
                   String visFruit, int visCount, boolean visChip) {
            this.name = name;
            this.cards = cards;
            this.chips = chips;
            this.active = active;
            this.visFruit = visFruit;
            this.visCount = visCount;
            this.visChip = visChip;
        }

        public boolean hasVisible() {
            return visFruit != null;
        }
    }

    private StateView(String turn, boolean bellable, int house, int chipSymbols) {
        this.turn = turn;
        this.bellable = bellable;
        this.house = house;
        this.chipSymbols = chipSymbols;
    }

    /** Parses one {@code STATE|...} line. */
    public static StateView parse(String line) {
        String[] tokens = line.split("\\|");
        String turn = "";
        boolean bellable = false;
        int house = 0;
        int chipSym = 0;
        String fruitsRaw = "";
        List<String> playerTokens = new ArrayList<>();

        for (int i = 1; i < tokens.length; i++) {
            String t = tokens[i];
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = t.substring(0, eq);
            String val = t.substring(eq + 1);
            switch (key) {
                case "turn": turn = val; break;
                case "bellable": bellable = Boolean.parseBoolean(val); break;
                case "house": house = parseInt(val); break;
                case "chipsym": chipSym = parseInt(val); break;
                case "fruits": fruitsRaw = val; break;
                case "p": playerTokens.add(val); break;
                default: break;
            }
        }

        StateView view = new StateView(turn, bellable, house, chipSym);
        if (!fruitsRaw.isBlank()) {
            for (String pair : fruitsRaw.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    view.fruits.put(kv[0], parseInt(kv[1]));
                }
            }
        }
        for (String pt : playerTokens) {
            String[] f = pt.split(",");
            if (f.length >= 4) {
                String visFruit = null;
                int visCount = 0;
                boolean visChip = false;
                if (f.length >= 5 && !"-".equals(f[4])) {
                    String[] v = f[4].split(":");
                    if (v.length == 3) {
                        visFruit = v[0];
                        visCount = parseInt(v[1]);
                        visChip = "1".equals(v[2]);
                    }
                }
                view.players.add(new PlayerInfo(f[0], parseInt(f[1]), parseInt(f[2]),
                        Protocol.STATUS_ACTIVE.equals(f[3]), visFruit, visCount, visChip));
            }
        }
        return view;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Title-cases a fruit enum name, e.g. "BANANA" -> "Banana". */
    public static String prettyFruit(String enumName) {
        if (enumName == null || enumName.isEmpty()) {
            return enumName;
        }
        return enumName.charAt(0) + enumName.substring(1).toLowerCase();
    }

    /** Human-readable board string. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("  +-- Table ");
        sb.append(bellable ? "** BELL! **" : "").append("\n");
        sb.append("  | Fruits: ");
        if (fruits.isEmpty()) {
            sb.append("(none)");
        } else {
            fruits.forEach((f, c) -> sb.append(prettyFruit(f)).append("=").append(c).append(" "));
        }
        sb.append("\n");
        sb.append("  | Chips: ").append(chipSymbols).append("/3   House: ").append(house).append("\n");
        for (PlayerInfo p : players) {
            sb.append("  | ").append(p.name.equals(turn) ? "> " : "  ")
                    .append(p.name).append(": cards ").append(p.cards)
                    .append(", chips ").append(p.chips);
            if (p.hasVisible()) {
                sb.append(" | shows ").append(prettyFruit(p.visFruit)).append(" x").append(p.visCount)
                        .append(p.visChip ? "[chip]" : "");
            }
            sb.append(p.active ? "" : " [OUT]").append("\n");
        }
        sb.append("  +--");
        return sb.toString();
    }
}
