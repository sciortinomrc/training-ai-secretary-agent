package io.meterian.aicalendar.tools;

import java.util.regex.Pattern;

/**
 * Decides whether the user's own words ask to send, for example "send it" or "send both my drafts". Words such as
 * "don't send", "not send yet" or "send it later" do not count.
 */
final class SendRequestReader {

    private static final Pattern SEND_WORD = Pattern.compile("\\bsend\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HELD_BACK_SEND = Pattern.compile(
            "\\b(don['’]?t|do not|not|never)\\s+send\\b|\\bsend\\b[^.?!]*\\b(later|yet)\\b",
            Pattern.CASE_INSENSITIVE);

    private SendRequestReader() {
    }

    static boolean isExplicitSendRequest(String userMessage) {
        return userMessage != null
                && SEND_WORD.matcher(userMessage).find()
                && !HELD_BACK_SEND.matcher(userMessage).find();
    }
}
