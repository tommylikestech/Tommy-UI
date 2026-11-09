package com.android.settings.tommyui;

public class TrialFinishTest {
    private static void expect(TrialFinish want, boolean pending, boolean audited,
            boolean requested, boolean selecting, int state) {
        TrialFinish got = TrialFinish.action(pending, audited, requested, selecting, state);
        if (got != want) throw new AssertionError("Expected " + want + " but got " + got);
    }

    public static void main(String[] args) {
        expect(TrialFinish.NORMAL, false, false, false, false, 0);
        expect(TrialFinish.WAIT, true, false, false, false, 0);
        expect(TrialFinish.WAIT, true, false, true, false, 6);
        expect(TrialFinish.SELECT, true, true, false, false, 0);
        expect(TrialFinish.BUSY, true, true, true, true, 0);
        expect(TrialFinish.BUSY, true, true, true, false, 5);
        expect(TrialFinish.BUSY, true, true, false, false, 6);
        expect(TrialFinish.RESTART, true, true, true, false, 6);
        expect(TrialFinish.RESTART, false, true, true, false, 6);
        System.out.println("Staged, stale, busy and reboot-ready states passed.");
    }
}
