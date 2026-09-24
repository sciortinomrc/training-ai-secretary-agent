package io.meterian.aicalendar.channel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** A UserChannel for tests: records what is printed and answers questions from a queue (default: no). */
public class RecordingUserChannel implements UserChannel {

    public final List<String> replies = Collections.synchronizedList(new ArrayList<>());
    public final List<String> alerts = Collections.synchronizedList(new ArrayList<>());
    public final List<String> questions = Collections.synchronizedList(new ArrayList<>());
    public final Deque<Boolean> answers = new ArrayDeque<>();

    @Override
    public String readLine() {
        return null;
    }

    @Override
    public void printReply(String text) {
        replies.add(text);
    }

    @Override
    public void printAlert(String text) {
        alerts.add(text);
    }

    @Override
    public boolean askYesNo(String question) {
        questions.add(question);
        return !answers.isEmpty() && answers.removeFirst();
    }
}
