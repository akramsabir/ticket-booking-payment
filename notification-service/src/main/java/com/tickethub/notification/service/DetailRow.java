package com.tickethub.notification.service;

/** One "Label: value" line in the email's details table (a class with getters so Thymeleaf can read it). */
public class DetailRow {

    private final String label;
    private final String value;

    public DetailRow(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() { return label; }
    public String getValue() { return value; }
}
