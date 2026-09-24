package io.meterian.aicalendar.chat;

public class ChatModelException extends RuntimeException {

    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
