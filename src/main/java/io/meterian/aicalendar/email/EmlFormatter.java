package io.meterian.aicalendar.email;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formats one email as standard .eml text (MIME): the headers, the text part and the attachment part, or only
 * the text when there is no attachment.
 * Mail programs such as Thunderbird or Outlook can open the result.
 */
public class EmlFormatter {

    private static final String LINE_BREAK = "\r\n";
    private static final String PART_BOUNDARY = "ai-calendar-part-boundary";

    /** An email with an attachment has two parts; an email without one is plain text. */
    public String formatEmail(Email email, ZonedDateTime sentAt) {
        StringBuilder eml = new StringBuilder();
        appendAddressHeaders(eml, email, sentAt);
        if (email.attachment == null) {
            appendPlainTextBody(eml, email.body);
            return eml.toString();
        }
        appendLine(eml, "Content-Type: multipart/mixed; boundary=\"" + PART_BOUNDARY + "\"");
        appendLine(eml, "");
        appendTextPart(eml, email.body);
        appendAttachmentPart(eml, email.attachment);
        appendLine(eml, "--" + PART_BOUNDARY + "--");
        return eml.toString();
    }

    private static void appendAddressHeaders(StringBuilder eml, Email email, ZonedDateTime sentAt) {
        appendLine(eml, "From: " + formatHeaderAddress(email.from));
        appendLine(eml, "To: " + formatHeaderAddress(email.to));
        appendLine(eml, "Subject: " + email.subject);
        appendLine(eml, "Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(sentAt));
        appendLine(eml, "MIME-Version: 1.0");
    }

    private static void appendPlainTextBody(StringBuilder eml, String body) {
        appendLine(eml, "Content-Type: text/plain; charset=UTF-8");
        appendLine(eml, "Content-Transfer-Encoding: 8bit");
        appendLine(eml, "");
        appendLine(eml, convertToEmailLineBreaks(body));
    }

    private static void appendTextPart(StringBuilder eml, String body) {
        appendLine(eml, "--" + PART_BOUNDARY);
        appendLine(eml, "Content-Type: text/plain; charset=UTF-8");
        appendLine(eml, "Content-Transfer-Encoding: 8bit");
        appendLine(eml, "");
        appendLine(eml, convertToEmailLineBreaks(body));
    }

    private static void appendAttachmentPart(StringBuilder eml, EmailAttachment attachment) {
        appendLine(eml, "--" + PART_BOUNDARY);
        appendLine(eml, "Content-Type: " + attachment.contentType);
        appendLine(eml, "Content-Disposition: attachment; filename=\"" + attachment.fileName + "\"");
        appendLine(eml, "Content-Transfer-Encoding: 8bit");
        appendLine(eml, "");
        String content = convertToEmailLineBreaks(attachment.content);
        eml.append(content.endsWith(LINE_BREAK) ? content : content + LINE_BREAK);
    }

    private static String formatHeaderAddress(EmailAddress emailAddress) {
        return "\"" + emailAddress.name + "\" <" + emailAddress.address + ">";
    }

    /** Email text uses CR LF line breaks. */
    private static String convertToEmailLineBreaks(String text) {
        return text.replace("\r\n", "\n").replace("\n", LINE_BREAK);
    }

    private static void appendLine(StringBuilder eml, String line) {
        eml.append(line).append(LINE_BREAK);
    }
}
