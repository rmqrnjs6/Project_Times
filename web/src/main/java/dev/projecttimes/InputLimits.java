package dev.projecttimes;

import java.util.Map;

/** Existing input policy, shared by server validation and MVC views. Not runtime tuning knobs. */
public final class InputLimits {
    public static final int QUESTION_CHARS = 2000;
    public static final int NOTE_CHARS = 4000;
    public static final int SOURCE_URL_CHARS = 2048;
    public static final int PLAIN_TEXT_BYTES = 20000;
    public static final int REQUEST_BODY_BYTES = 32768;
    private static final Map<String,Integer> VIEW = Map.of(
        "question",QUESTION_CHARS,"note",NOTE_CHARS,"sourceUrl",SOURCE_URL_CHARS);
    private InputLimits() {}
    public static Map<String,Integer> forView() { return VIEW; }
}
