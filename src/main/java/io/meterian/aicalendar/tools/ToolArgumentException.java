package io.meterian.aicalendar.tools;

/** A tool argument is missing or has a bad format. The message goes to the model. */
public class ToolArgumentException extends RuntimeException {

    public ToolArgumentException(String message) {
        super(message);
    }
}
