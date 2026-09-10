package com.tarkovinventory.client.screen.layout;

public class UILayout {

    private final UIRoot root;

    public UILayout(UIRoot root) {
        this.root = root;
    }

    public Panel equipment() {
        return new Panel(root.x, root.y, 180, 170);
    }

    public Panel grid() {
        return new Panel(root.x + 190, root.y, 240, 240);
    }

    public Panel loot() {
        return new Panel(root.x + 440, root.y, 180, 220);
    }

    public Panel vicinity() {
        return new Panel(root.x + 440, root.y + 230, 180, 120);
    }
}
