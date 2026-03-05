package net.uberfoo.jquote.jquote.quote;

public enum IntradaySession {
    REGULAR("Regular Session", false),
    EXTENDED("Extended Hours", true);

    private final String label;
    private final boolean includeExtendedHours;

    IntradaySession(String label, boolean includeExtendedHours) {
        this.label = label;
        this.includeExtendedHours = includeExtendedHours;
    }

    public String label() {
        return label;
    }

    public boolean includeExtendedHours() {
        return includeExtendedHours;
    }
}
