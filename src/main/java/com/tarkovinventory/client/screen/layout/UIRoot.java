package com.tarkovinventory.client.screen.layout;

public class UIRoot {

    public final int x;
    public final int y;

    public UIRoot(int screenWidth, int screenHeight) {
        this.x = (screenWidth - 650) / 2;
        this.y = (screenHeight - 350) / 2;
    }
}
