package halligalli.model;

/**
 * The four fruit kinds used in Halli Galli. Standard Halli Galli uses 4 fruits.
 */
public enum Fruit {
    BANANA("Banana"),
    STRAWBERRY("Strawberry"),
    LIME("Lime"),
    PLUM("Plum");

    private final String label;

    Fruit(String label) {
        this.label = label;
    }

    /** English display label. */
    public String label() {
        return label;
    }
}
