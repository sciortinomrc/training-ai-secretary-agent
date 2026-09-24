package io.meterian.aicalendar.email;

/** Delivers emails. This project writes them to files; a real SMTP sender could replace it later. */
public interface EmailSender {

    /** Delivers one email and returns where it went, for example a file path. Throws EmailException on failure. */
    String send(Email email);
}
