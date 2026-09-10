package com.tarkovinventory.inventory;

/**
 * Represents the width and height a particular item occupies in the Tarkov grid.
 */
public record GridSize(int width, int height) {

    public static final GridSize ONE_BY_ONE   = new GridSize(1, 1);
    public static final GridSize ONE_BY_TWO   = new GridSize(1, 2);
    public static final GridSize TWO_BY_ONE   = new GridSize(2, 1);
    public static final GridSize TWO_BY_TWO   = new GridSize(2, 2);
    public static final GridSize TWO_BY_THREE = new GridSize(2, 3);
    public static final GridSize TWO_BY_FOUR  = new GridSize(2, 4);
    public static final GridSize THREE_BY_ONE = new GridSize(3, 1);
    public static final GridSize THREE_BY_TWO = new GridSize(3, 2);
    public static final GridSize ONE_BY_THREE = new GridSize(1, 3);

    public int cells() {
        return width * height;
    }

    public GridSize rotated() {
        return new GridSize(height, width);
    }
}
