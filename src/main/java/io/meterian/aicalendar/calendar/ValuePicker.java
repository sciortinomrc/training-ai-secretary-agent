package io.meterian.aicalendar.calendar;

/** Picks the changed value when there is one, and the original value when there is not. */
final class ValuePicker {

    private ValuePicker() {
    }

    static <T> T pickChangedValue(T changed, T original) {
        return changed != null ? changed : original;
    }
}
