package io.meterian.aicalendar.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConsoleChannelTest {

    private static final String LINE_BREAK = System.lineSeparator();

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private ConsoleChannel buildChannel(Reader input) {
        return new ConsoleChannel(new BufferedReader(input), new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    private String readOutput() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void readLinePrintsPromptAndReturnsInput() {
        ConsoleChannel channel = buildChannel(new StringReader("hello\n"));

        assertEquals("hello", channel.readLine());
        assertNull(channel.readLine());
        assertTrue(readOutput().startsWith("> "));
    }

    @Test
    void alertWhileWaitingForInputPrintsThePromptAgain() {
        AtomicReference<ConsoleChannel> channelReference = new AtomicReference<>();
        Reader input = new StringReader("hello\n") {
            private boolean alerted;

            @Override
            public int read(char[] buffer, int offset, int length) throws IOException {
                if (!alerted) {
                    alerted = true;
                    channelReference.get().printAlert("ALERT");
                }
                return super.read(buffer, offset, length);
            }
        };
        ConsoleChannel channel = buildChannel(input);
        channelReference.set(channel);

        assertEquals("hello", channel.readLine());
        assertEquals("> " + LINE_BREAK + "ALERT" + LINE_BREAK + "> ", readOutput());
    }

    @Test
    void alertWhileNotWaitingPrintsOnlyTheAlert() {
        buildChannel(new StringReader("")).printAlert("ALERT");

        assertEquals("ALERT" + LINE_BREAK, readOutput());
    }

    @Test
    void askYesNoReadsSingleLetterAnswersAndEndOfInput() {
        assertTrue(buildChannel(new StringReader("Y\n")).askYesNo("Approve this action?"));
        assertFalse(buildChannel(new StringReader("no\n")).askYesNo("Approve this action?"));
        assertFalse(buildChannel(new StringReader("")).askYesNo("Approve this action?"));
        assertTrue(readOutput().contains("Approve this action? (y/n) "));
    }

    @Test
    void askYesNoAcceptsYesAndNo() {
        assertTrue(buildChannel(new StringReader("yes\n")).askYesNo("Approve this action?"));
        assertFalse(buildChannel(new StringReader("No\n")).askYesNo("Approve this action?"));
    }

    @Test
    void askYesNoAsksAgainAfterAnUnclearAnswer() {
        assertTrue(buildChannel(new StringReader("yes, send it\ny\n")).askYesNo("Approve this action?"));
        assertTrue(readOutput().contains("Please type y or n."));
    }
}
