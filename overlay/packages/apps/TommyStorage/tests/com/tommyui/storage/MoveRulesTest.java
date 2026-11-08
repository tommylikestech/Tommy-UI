package com.tommyui.storage;

public final class MoveRulesTest {
    private static int count;
    private static void check(boolean ok, String message) {
        count++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(MoveRules.protectedApp(true, false, false), "system app must stay protected");
        check(MoveRules.protectedApp(false, true, false), "updated system app must stay protected");
        check(MoveRules.protectedApp(false, false, true), "the mover must stay internal");
        check(!MoveRules.protectedApp(false, false, false), "downloaded app can be considered");
        check(!MoveRules.destinationAllowed(true, true, true, true, false), "no move to current volume");
        check(!MoveRules.destinationAllowed(false, false, true, false, true), "removed/unmounted card rejected");
        check(!MoveRules.destinationAllowed(false, true, false, false, true), "portable SD is not app storage");
        check(!MoveRules.destinationAllowed(false, true, true, false, false), "non-SD external target excluded");
        check(MoveRules.destinationAllowed(false, true, true, false, true), "adopted mounted SD allowed");
        check(MoveRules.destinationAllowed(false, true, true, true, false), "return to internal allowed");
        check(MoveRules.failure(-1).contains("free space"), "report full card accurately");
        check(MoveRules.failure(-8).contains("administrator"), "respect administrator protection");
        check(MoveRules.failure(-10).contains("Unlock"), "explain locked profiles");
        check(MoveRules.failure(-1234).contains("-1234"), "retain unknown Android failure status");
        System.out.println(count + " storage policy checks passed.");
    }
}
