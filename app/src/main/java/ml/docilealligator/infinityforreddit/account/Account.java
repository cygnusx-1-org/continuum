package ml.docilealligator.infinityforreddit.account;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Objects;

@Entity(tableName = "accounts")
public class Account implements Parcelable {
    public static final String ANONYMOUS_ACCOUNT = "-";
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "username")
    private final String accountName;
    @ColumnInfo(name = "profile_image_url")
    @Nullable
    private final String profileImageUrl;
    @ColumnInfo(name = "banner_image_url")
    @Nullable
    private final String bannerImageUrl;
    @ColumnInfo(name = "karma")
    private final int karma;
    @ColumnInfo(name = "access_token")
    @Nullable
    private String accessToken;
    @ColumnInfo(name = "refresh_token")
    @Nullable
    private final String refreshToken;
    @ColumnInfo(name = "code")
    @Nullable
    private final String code;
    @ColumnInfo(name = "is_current_user")
    private final boolean isCurrentUser;
    @ColumnInfo(name = "is_mod")
    private final boolean isMod;

    @Ignore
    protected Account(Parcel in) {
        accountName = Objects.requireNonNull(in.readString());
        profileImageUrl = in.readString();
        bannerImageUrl = in.readString();
        karma = in.readInt();
        accessToken = in.readString();
        refreshToken = in.readString();
        code = in.readString();
        isCurrentUser = in.readByte() != 0;
        isMod = in.readByte() != 0;
    }

    public static final Creator<Account> CREATOR = new Creator<Account>() {
        @Override
        public Account createFromParcel(Parcel in) {
            return new Account(in);
        }

        @Override
        public Account[] newArray(int size) {
            return new Account[size];
        }
    };

    @Ignore
    public static Account getAnonymousAccount() {
        return new Account(Account.ANONYMOUS_ACCOUNT, null, null, null, null, null, 0, false, false);
    }

    public Account(@NonNull String accountName, @Nullable String accessToken, @Nullable String refreshToken, @Nullable String code,
                   @Nullable String profileImageUrl, @Nullable String bannerImageUrl, int karma, boolean isCurrentUser, boolean isMod) {
        this.accountName = accountName;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.code = code;
        this.profileImageUrl = profileImageUrl;
        this.bannerImageUrl = bannerImageUrl;
        this.karma = karma;
        this.isCurrentUser = isCurrentUser;
        this.isMod = isMod;
    }

    @NonNull
    public String getAccountName() {
        return accountName;
    }

    @Nullable
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    @Nullable
    public String getBannerImageUrl() {
        return bannerImageUrl;
    }

    public int getKarma() {
        return karma;
    }

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(@Nullable String accessToken) {
        this.accessToken = accessToken;
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    @Nullable
    public String getCode() {
        return code;
    }

    public boolean isCurrentUser() {
        return isCurrentUser;
    }

    public boolean isMod() {
        return isMod;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(accountName);
        dest.writeString(profileImageUrl);
        dest.writeString(bannerImageUrl);
        dest.writeInt(karma);
        dest.writeString(accessToken);
        dest.writeString(refreshToken);
        dest.writeString(code);
        dest.writeByte((byte) (isCurrentUser ? 1 : 0));
        dest.writeByte((byte) (isMod ? 1 : 0));
    }

    public String getJSONModel() {
        return new Gson().toJson(this);
    }

    public static Account fromJson(@Nullable String json) throws JsonParseException {
        return new Gson().fromJson(json, Account.class);
    }
}
