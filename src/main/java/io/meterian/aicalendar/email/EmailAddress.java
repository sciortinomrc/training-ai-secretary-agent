package io.meterian.aicalendar.email;

/** A person's name and email address. */
public final class EmailAddress {
    public final String name;
    public final String address;

    public EmailAddress(String name, String address) {
        this.name = name;
        this.address = address;
    }

    /** For example: Anna Rossi <anna@example.com> */
    public String formatForDisplay() {
        return name + " <" + address + ">";
    }
}
