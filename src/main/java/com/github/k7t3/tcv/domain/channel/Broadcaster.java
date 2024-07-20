/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
                Objects.equals(userLogin, that.userLogin);
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
