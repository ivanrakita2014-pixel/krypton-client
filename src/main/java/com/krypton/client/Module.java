package com.krypton.client;

public final class Module {
    public enum Category { RENDER, WORLD, HUD }
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;

    public Module(String name, String description, Category category, boolean enabled) {
        this.name = name; this.description = description; this.category = category; this.enabled = enabled;
    }
    public String name() { return name; }
    public String description() { return description; }
    public Category category() { return category; }
    public boolean enabled() { return enabled; }
    public void toggle() { enabled = !enabled; }
    public void setEnabled(boolean value) { enabled = value; }
}
