package io.meterian.aicalendar.channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

/** The terminal version of UserChannel. An alert that arrives while the user types prints the prompt again. */
public class ConsoleChannel implements UserChannel {

    static final String PROMPT = "> ";
    private static final String YES_OR_NO_REMINDER = "Please type y or n. ";
    private static final List<String> YES_ANSWERS = List.of("y", "yes");
    private static final List<String> NO_ANSWERS = List.of("n", "no");

    private final BufferedReader in;
    private final PrintStream out;
    /** The prompt on screen while waiting for input, or null. */
    private volatile String activePrompt;

    public ConsoleChannel(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    @Override
    public String readLine() {
        return readAnswer(PROMPT);
    }

    @Override
    public void printReply(String text) {
        synchronized (out) {
            out.println(text);
            out.flush();
        }
    }

    @Override
    public void printAlert(String text) {
        synchronized (out) {
            String prompt = activePrompt;
            if (prompt != null) {
                out.println();
            }
            out.println(text);
            if (prompt != null) {
                out.print(prompt);
            }
            out.flush();
        }
    }

    /** Accepts y or yes, and n or no. Asks again after any other answer. The end of input counts as no. */
    @Override
    public boolean askYesNo(String question) {
        String answer = readAnswer(question + " (y/n) ");
        while (answer != null) {
            String normalizedAnswer = answer.trim().toLowerCase(Locale.ROOT);
            if (YES_ANSWERS.contains(normalizedAnswer)) {
                return true;
            }
            if (NO_ANSWERS.contains(normalizedAnswer)) {
                return false;
            }
            answer = readAnswer(YES_OR_NO_REMINDER);
        }
        return false;
    }

    private String readAnswer(String prompt) {
        synchronized (out) {
            out.print(prompt);
            out.flush();
            activePrompt = prompt;
        }
        try {
            return in.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            activePrompt = null;
        }
    }
}
