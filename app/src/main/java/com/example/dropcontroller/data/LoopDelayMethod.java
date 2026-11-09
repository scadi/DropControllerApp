package com.example.dropcontroller.data;

/**
 * How the next sequence is started in Loop Mode.
 */
public enum LoopDelayMethod {
    /** A countdown (the wait time) runs after each sequence. */
    TIMER,
    /** Each sequence waits for the Loop Page START button. */
    BUTTON
}
