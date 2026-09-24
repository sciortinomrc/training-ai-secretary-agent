package io.meterian.aicalendar.email;

/** Turns an email address into a safe part of a file name, for example anna@example.com -> anna_example.com. */
final class EmailFileNames {

    private EmailFileNames() {
    }

    static String convertAddressToFileNamePart(String address) {
        return address.replaceAll("[^A-Za-z0-9.-]", "_");
    }
}
