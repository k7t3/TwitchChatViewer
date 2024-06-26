package com.github.k7t3.tcv.domain.channel;

import java.util.Objects;
import java.util.Optional;

/**
 * チャンネルのブロードキャスター基本情報
 */
public final class Broadcaster {

    private final String userId;
    private final String userLogin;
    private final String userName;

    private final String profileImageUrl;

    private final String offlineImageUrl;

    public Broadcaster(String userId, String userLogin, String userName, String profileImageUrl, String offlineImageUrl) {
        this.userId = userId;
        this.userLogin = userLogin;
        this.userName = userName;
        this.profileImageUrl = profileImageUrl;
        this.offlineImageUrl = offlineImageUrl == null || offlineImageUrl.trim().isEmpty() ? null : offlineImageUrl.trim();
    }

    public String getUserId() {
        return userId;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public String getUserName() {
        return userName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public Optional<String> getOfflineImageUrl() {
        return Optional.ofNullable(offlineImageUrl);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Broadcaster that)) return false;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(userLogin, that.userLogin) &&
                Objects.equals(userName, that.userName) &&
                Objects.equals(profileImageUrl, that.profileImageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, userLogin);
    }

    @Override
    public String toString() {
        return "Broadcaster{" +
                "userId='" + userId + '\'' +
                ", userLogin='" + userLogin + '\'' +
                ", userName='" + userName + '\'' +
                ", profileImageUrl='" + profileImageUrl + '\'' +
                '}';
    }
}
