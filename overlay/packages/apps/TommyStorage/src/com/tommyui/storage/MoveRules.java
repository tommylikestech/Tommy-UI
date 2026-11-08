package com.tommyui.storage;

/** Small Android-independent rules, also exercised by the host tests. */
final class MoveRules {
    static boolean protectedApp(boolean system, boolean updatedSystem, boolean self) {
        return system || updatedSystem || self;
    }

    static boolean destinationAllowed(boolean current, boolean mounted,
            boolean privateVolume, boolean internal, boolean sdCard) {
        return !current && mounted && privateVolume && (internal || sdCard);
    }

    static String failure(int status) {
        switch (status) {
            case -1: return "There is not enough free space at the destination.";
            case -2: return "This app is no longer installed.";
            case -3: return "Android does not allow system apps to move.";
            case -5: return "The destination is unavailable or cannot hold this app.";
            case -6: return "Android could not complete the move. Check the card and try again.";
            case -7: return "Android is already moving an app. Wait for that move to finish.";
            case -8: return "A device administrator protects this app from moving.";
            case -9: return "This device does not allow this app on internal storage.";
            case -10: return "Unlock every user profile associated with this app, then try again.";
            default: return "Android could not complete the move (status " + status + ").";
        }
    }
}
