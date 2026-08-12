package ml.docilealligator.infinityforreddit.events;

import androidx.annotation.Nullable;

public class SwitchAccountEvent {
    @Nullable
    public String excludeActivityClassName;

    public SwitchAccountEvent() {
    }

    public SwitchAccountEvent(String excludeActivityClassName) {
        this.excludeActivityClassName = excludeActivityClassName;
    }
}
