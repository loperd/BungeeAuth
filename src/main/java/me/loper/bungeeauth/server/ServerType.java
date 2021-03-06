package me.loper.bungeeauth.server;

public enum ServerType {
    LOGIN("login"),
    GAME("game"),
    UNKNOWN("unknown");

    private final String type;

    ServerType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
