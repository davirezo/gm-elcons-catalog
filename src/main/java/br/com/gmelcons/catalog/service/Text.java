package br.com.gmelcons.catalog.service;

final class Text {
    private Text() {
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
