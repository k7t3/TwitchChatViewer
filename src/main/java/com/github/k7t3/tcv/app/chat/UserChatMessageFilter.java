package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.domain.chat.ChatData;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class UserChatMessageFilter implements ChatMessageFilter {

    public static final UserChatMessageFilter DEFAULT = new UserChatMessageFilter(List.of());

    public record FilteredUser(String userId, String comment) {
        public void write(DataOutputStream os) throws IOException {
            os.writeUTF(userId);
            os.writeUTF(comment);
        }
        public static FilteredUser restore(DataInputStream is) throws IOException {
            var userId = is.readUTF();
            var comment = is.readUTF();
            return new FilteredUser(userId, comment);
        }
    }

    private final CopyOnWriteArraySet<FilteredUser> users;

    public UserChatMessageFilter(List<FilteredUser> users) {
        this.users = new CopyOnWriteArraySet<>(users);
    }

    public Set<FilteredUser> getUsers() {
        return users;
    }

    @Override
    public boolean test(ChatData chatData) {
        if (users.isEmpty())
            return true;

        return users.stream()
                .map(u -> u.userId)
                .noneMatch(id -> chatData.userId().equalsIgnoreCase(id));
    }

    @Override
    public byte[] serialize() {
        try (var baos = new ByteArrayOutputStream();
             var os = new DataOutputStream(baos)) {

            for (var user : users)
                user.write(os);

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static UserChatMessageFilter deserialize(byte[] bytes) {
        try (var bais = new ByteArrayInputStream(bytes);
             var dis = new DataInputStream(bais)) {

            var users = new ArrayList<FilteredUser>();

            while (0 < dis.available()) {
                users.add(FilteredUser.restore(dis));
            }

            return new UserChatMessageFilter(users);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
