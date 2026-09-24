package io.meterian.aicalendar.email;

/** One email with one attachment. */
public final class Email {
    public final EmailAddress from;
    public final EmailAddress to;
    public final String subject;
    public final String body;
    public final EmailAttachment attachment;

    public Email(EmailAddress from, EmailAddress to, String subject, String body, EmailAttachment attachment) {
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.attachment = attachment;
    }
}
