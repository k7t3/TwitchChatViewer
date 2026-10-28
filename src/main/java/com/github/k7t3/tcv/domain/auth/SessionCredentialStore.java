package com.github.k7t3.tcv.domain.auth;

import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import com.github.philippheuer.credentialmanager.domain.Credential;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** SDK と API に、その認証試行・セッションだけの資格情報と保存権を渡す。 */
public final class SessionCredentialStore implements CredentialStore {
    private enum State { ACTIVE, RETIRED, INVALID }
    private final CredentialStore backing;
    private final Object writes = new Object();
    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
    private final AtomicBoolean notified = new AtomicBoolean();
    private final ConcurrentHashMap<String, Set<String>> tokenHistory = new ConcurrentHashMap<>();
    private volatile Snapshot lease;
    private volatile List<Credential> visible;
    private volatile OAuth2Credential own;
    private volatile boolean ownsStoredCredential;
    private volatile Runnable invalidated;
    private volatile Consumer<OAuth2Credential> updated = ignored -> {};

    public SessionCredentialStore(CredentialStore backing) {
        this.backing = Objects.requireNonNull(backing);
        var initial = backing.snapshot();
        lease = new Snapshot(initial.revision(), copies(initial.credentials()));
        visible = copies(initial.credentials());
        remember(visible);
    }

    public SessionCredentialStore(CredentialStore backing, OAuth2Credential credential) {
        this(backing);
        bind(credential);
    }

    public void bind(OAuth2Credential credential) {
        synchronized (writes) {
            checkWritable();
            if (own != null) throw new IllegalStateException("credential scope is already bound");
            own = copy(credential);
            ownsStoredCredential = lease.credentials().stream().anyMatch(value -> sameCredential(value, credential));
            visible = List.of(copy(credential));
            remember(visible);
        }
    }

    public boolean isRetired() { return state.get() == State.RETIRED; }
    public boolean isInvalid() { return state.get() == State.INVALID; }
    public void retire() { state.compareAndSet(State.ACTIVE, State.RETIRED); }
    public void awaitWrites() { synchronized (writes) { /* 保存の確定を背景で待つ。 */ } }
    public OAuth2Credential credential() { return copy(Objects.requireNonNull(own)); }

    public Set<String> tokens() {
        var selected = own;
        if (selected != null) return Set.copyOf(tokenHistory.getOrDefault(key(selected), Set.of()));
        return tokenHistory.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public void onUpdated(Consumer<OAuth2Credential> listener) { updated = Objects.requireNonNull(listener); }
    public void onInvalidated(Runnable listener) {
        invalidated = Objects.requireNonNull(listener);
        notifyInvalidation();
    }

    public void invalidate() {
        state.compareAndSet(State.ACTIVE, State.INVALID);
        notifyInvalidation();
    }

    private void notifyInvalidation() {
        var listener = invalidated;
        if (state.get() == State.INVALID && listener != null && notified.compareAndSet(false, true)) listener.run();
    }

    @Override public List<Credential> loadCredentials() { return new ArrayList<>(copies(visible)); }
    @Override public Snapshot snapshot() {
        synchronized (writes) { return new Snapshot(lease.revision(), copies(visible)); }
    }
    @Override public Optional<Credential> getCredentialByUserId(String id) {
        return loadCredentials().stream().filter(value -> id != null && id.equalsIgnoreCase(value.getUserId())).findFirst();
    }

    @Override
    public void saveCredentials(List<Credential> credentials) {
        try {
            synchronized (writes) {
                checkWritable();
                if (commit(credentials, () -> true).isEmpty()) {
                    checkWritable();
                    state.compareAndSet(State.ACTIVE, State.INVALID);
                    throw new InvalidCredentialException();
                }
            }
        } finally { notifyInvalidation(); }
    }

    @Override
    public Optional<Snapshot> compareAndSet(Snapshot expected, List<Credential> credentials, BooleanSupplier permitted) {
        synchronized (writes) {
            if (!expected.revision().equals(lease.revision()) || state.get() != State.ACTIVE) return Optional.empty();
            return commit(credentials, permitted);
        }
    }

    private Optional<Snapshot> commit(List<Credential> credentials, BooleanSupplier permitted) {
        var next = copies(credentials);
        var replacement = next;
        if (own != null) {
            if (!ownsStoredCredential) return Optional.empty();
            if (next.size() > 1 || next.stream().anyMatch(value -> !sameAccount(value, own))) {
                throw new IllegalArgumentException("credentials do not belong to this session");
            }
            replacement = new ArrayList<>(lease.credentials());
            replacement.removeIf(value -> sameAccount(value, own));
            replacement.addAll(0, next);
        }
        // 保存の許可判定を通過した更新は、retire と競合しても履歴に残す。
        remember(next);
        var result = backing.compareAndSet(lease, replacement, () -> state.get() == State.ACTIVE && permitted.getAsBoolean());
        if (result.isEmpty()) return Optional.empty();
        lease = new Snapshot(result.get().revision(), copies(result.get().credentials()));
        visible = next;
        if (own != null && !next.isEmpty()) {
            own = copy((OAuth2Credential) next.getFirst());
            updated.accept(copy(own));
        }
        return Optional.of(new Snapshot(lease.revision(), copies(visible)));
    }

    @Override public void clearCredentials() {
        var selected = own;
        if (selected == null) saveCredentials(List.of());
        else removeCredential(selected);
    }

    @Override
    public boolean removeCredential(OAuth2Credential expected) {
        synchronized (writes) {
            if (own == null || !ownsStoredCredential || !sameCredential(own, expected)) return false;
            var next = new ArrayList<>(lease.credentials());
            next.removeIf(value -> sameCredential(value, own));
            var result = backing.compareAndSet(lease, next, () -> true);
            if (result.isEmpty()) return false;
            lease = new Snapshot(result.get().revision(), copies(result.get().credentials()));
            visible = List.of();
            ownsStoredCredential = false;
            return true;
        }
    }

    private void checkWritable() {
        if (isRetired()) throw new CancellationException("credential session has ended");
        if (isInvalid()) throw new InvalidCredentialException();
    }

    private void remember(List<Credential> values) {
        for (var value : values) {
            if (value instanceof OAuth2Credential oauth && oauth.getAccessToken() != null) {
                tokenHistory.computeIfAbsent(key(oauth), ignored -> ConcurrentHashMap.newKeySet()).add(oauth.getAccessToken());
            }
        }
    }

    private static String key(OAuth2Credential value) { return value.getIdentityProvider() + ":" + value.getUserId(); }
    private static boolean sameAccount(Credential first, OAuth2Credential second) {
        return first instanceof OAuth2Credential oauth
                && Objects.equals(oauth.getIdentityProvider(), second.getIdentityProvider())
                && Objects.equals(oauth.getUserId(), second.getUserId());
    }
    private static boolean sameCredential(Credential first, OAuth2Credential second) {
        return sameAccount(first, second) && first instanceof OAuth2Credential oauth
                && Objects.equals(oauth.getAccessToken(), second.getAccessToken())
                && Objects.equals(oauth.getRefreshToken(), second.getRefreshToken());
    }
    private static List<Credential> copies(List<Credential> values) {
        return values.stream().map(value -> {
            if (!(value instanceof OAuth2Credential oauth)) throw new IllegalArgumentException("unsupported credential type");
            return (Credential) copy(oauth);
        }).toList();
    }
    private static OAuth2Credential copy(OAuth2Credential value) {
        // SDK updateCredential mutates this list; isolate it without making it unmodifiable.
        var result = new OAuth2Credential(value.getIdentityProvider(), value.getAccessToken(), value.getRefreshToken(),
                value.getUserId(), value.getUserName(), value.getExpiresIn(), new ArrayList<>(value.getScopes()),
                new HashMap<>(value.getContext()));
        result.setReceivedAt(value.getReceivedAt());
        return result;
    }
}
