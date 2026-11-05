package com.focusflow.models;

public class Subtask {
    public long id;
    public String title = "";
    public boolean done;
    public Long doneAt;

    public Subtask() {}
    public Subtask(String title, boolean done) { this.title = title; this.done = done; }

    public Subtask copy() {
        Subtask s = new Subtask(title, done);
        s.id = id;
        s.doneAt = doneAt;
        return s;
    }
}
