package io.meterian.aicalendar.calendar;

/** Picks the changed value when there is one, and the original value when there is not. */
public final class ValuePicker {

    private ValuePicker() {
    }

    public static <T> T pickChangedValue(T changed, T original) {
        return changed != null ? changed : original;
    }
}
