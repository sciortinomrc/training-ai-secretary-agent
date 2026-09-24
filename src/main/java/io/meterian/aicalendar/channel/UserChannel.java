package io.meterian.aicalendar.channel;

/** How the agent talks to the user. The terminal is one version; a UI can be another. */
public interface UserChannel {

    /** Reads the next request. Returns null at the end of input. */
    String readLine();

    void printReply(String text);

    /** May be called from the alert thread at any time. */
    void printAlert(String text);

    boolean askYesNo(String question);
}
