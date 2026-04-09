package com.mbest700.settings;

public class Setting {
    public String name;
    public double dValue;
    public boolean bValue;
    public boolean isSlider;

    public Setting(String name, double defaultValue) {
        this.name = name; this.dValue = defaultValue; this.isSlider = true;
    }
    public Setting(String name, boolean defaultValue) {
        this.name = name; this.bValue = defaultValue; this.isSlider = false;
    }
}

