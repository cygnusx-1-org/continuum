package ml.docilealligator.infinityforreddit.events;

public class ChangeAutoplayVideoControllerUIEvent {
    public boolean legacyAutoplayVideoControllerUI;

    public ChangeAutoplayVideoControllerUIEvent(boolean legacyAutoplayVideoControllerUI) {
        this.legacyAutoplayVideoControllerUI = legacyAutoplayVideoControllerUI;
    }
}
