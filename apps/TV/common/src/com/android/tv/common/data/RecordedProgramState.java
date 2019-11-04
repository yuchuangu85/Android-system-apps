package com.android.tv.common.data;

/** The recording state. */
// TODO(b/25023911): Use @SimpleEnum when it is supported by AutoValue
public enum RecordedProgramState {
    // TODO(b/71717809): Document each state.
    NOT_SET,
    STARTED,
    FINISHED,
    PARTIAL,
    FAILED,
    DELETE,
    DELETED,
}
