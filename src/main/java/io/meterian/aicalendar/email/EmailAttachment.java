package io.meterian.aicalendar.email;

/** One file attached to an email, as text. */
public final class EmailAttachment {
    public final String fileName;
    public final String contentType;
    public final String content;

    public EmailAttachment(String fileName, String contentType, String content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
    }
}
