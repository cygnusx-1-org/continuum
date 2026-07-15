package ml.docilealligator.infinityforreddit.user;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;

public class UserWithSelection implements Parcelable {
    private final String name;
    private final String iconUrl;
    private boolean selected;

    public UserWithSelection(String name, String iconUrl) {
        this.name = name;
        this.iconUrl = iconUrl;
        selected = false;
    }

    protected UserWithSelection(Parcel in) {
        name = Objects.requireNonNull(in.readString());
        iconUrl = Objects.requireNonNull(in.readString());
        selected = in.readByte() != 0;
    }

    public static final Creator<UserWithSelection> CREATOR = new Creator<UserWithSelection>() {
        @Override
        public UserWithSelection createFromParcel(Parcel in) {
            return new UserWithSelection(in);
        }

        @Override
        public UserWithSelection[] newArray(int size) {
            return new UserWithSelection[size];
        }
    };

    public String getName() {
        return name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public static ArrayList<UserWithSelection> convertSubscribedUsers(
            List<SubscribedUserData> subscribedUserData) {
        ArrayList<UserWithSelection> userWithSelections = new ArrayList<>();
        for (SubscribedUserData s : subscribedUserData) {
            userWithSelections.add(new UserWithSelection(s.getName(), s.getIconUrl()));
        }

        return userWithSelections;
    }

    public static UserWithSelection convertUser(UserData user) {
        return new UserWithSelection(user.getName(), user.getIconUrl());
    }

    public int compareName(UserWithSelection userWithSelection) {
        if (userWithSelection != null) {
            return name.compareToIgnoreCase(userWithSelection.getName());
        } else {
            return -1;
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof UserWithSelection)) {
            return false;
        } else {
            return this.getName().compareToIgnoreCase(((UserWithSelection) obj).getName()) == 0;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(name);
        parcel.writeString(iconUrl);
        parcel.writeByte((byte) (selected ? 1 : 0));
    }
}
