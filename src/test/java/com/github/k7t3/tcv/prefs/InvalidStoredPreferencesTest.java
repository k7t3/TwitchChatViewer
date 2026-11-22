package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.view.chat.ChatFont;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class InvalidStoredPreferencesTest {
    private static byte[] fontBytes(String family, double size, boolean trailing) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeUTF(family); out.writeDouble(size);
            if (trailing) out.writeByte(1);
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(strings = {"truncated", "blank", "zero", "negative", "nan", "infinite", "trailing"})
    void malformedFontsFallBackInMemoryWithoutReplacingTheStoredBytes(String invalid, FxRobot robot) throws Exception {
        var node = new MemoryPreferences();
        var data = switch (invalid) {
            case "truncated" -> new byte[] {0, 1};
            case "blank" -> fontBytes(" ", 16, false);
            case "zero" -> fontBytes("System", 0, false);
            case "negative" -> fontBytes("System", -1, false);
            case "nan" -> fontBytes("System", Double.NaN, false);
            case "infinite" -> fontBytes("System", Double.POSITIVE_INFINITY, false);
            case "trailing" -> fontBytes("System", 16, true);
            default -> throw new AssertionError();
        };
        node.putByteArray("chat.font.family", data);
        robot.interact(() -> assertThat(new AppPreferences(node).getChatPreferences().getFont()).isEqualTo(new FontSettings(ChatFont.getDefault().getFamily(), ChatFont.getDefault().getSize())));
        assertThat(node.getByteArray("chat.font.family", null)).containsExactly(data);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-2147483648"})
    void nonPositiveCacheSizesUseTheDefaultWithoutChangingTheRawValue(String value) {
        var node = new MemoryPreferences();
        node.put("chat.cache.size", value);
        assertThat(new AppPreferences(node).getChatPreferences().getChatCacheSize()).isEqualTo(256);
        assertThat(node.get("chat.cache.size", null)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "-0.1", "0", "0.099", "1.1"})
    void invalidOpacityUsesTheVisibleDefaultWithoutChangingTheRawValue(String value) {
        var node = new MemoryPreferences();
        node.put("floatable.chat.opacity", value);
        assertThat(new AppPreferences(node).getChatPreferences().getFloatingChatOpacity()).isEqualTo(0.7);
        assertThat(node.get("floatable.chat.opacity", null)).isEqualTo(value);
    }

    @Test void anUnknownGroupSortSelectionFallsBackBeforePresentation() {
        var node = new MemoryPreferences();
        node.put("group.order.item", "NOT_A_SORT_ORDER");
        assertThat(new AppPreferences(node).getStatePreferences().getGroupOrderItem()).isEqualTo("NAME");
        assertThat(node.get("group.order.item", null)).isEqualTo("NOT_A_SORT_ORDER");
    }

    @Test void unreadableDatabasePathIsNotSilentlyReplacedByAnotherDatabaseLocation() {
        var node = spy(new MemoryPreferences());
        var failure = new SecurityException("preference storage is unavailable");
        doThrow(failure).when(node).get(eq("user.data.file"), any());
        var general = new AppPreferences(node).getGeneralPreferences();
        assertThatThrownBy(general::getUserDataFilePath).isSameAs(failure);
        verify(node, never()).put(anyString(), anyString());
    }

    @Test void invalidCommittedValuesAreRejectedBeforeAnyCachedPropertyChanges(FxRobot robot) {
        var node = new MemoryPreferences();
        var prefs = new AppPreferences(node);
        robot.interact(() -> {
            var chat = prefs.getChatPreferences();
            assertThat(chat.isShowUserName()).isTrue();
            assertThat(chat.getChatCacheSize()).isEqualTo(256);
            assertThatThrownBy(() -> prefs.applyCommittedValues(Map.of("chat.show.username", Optional.of("false"),
                    "chat.cache.size", Optional.of("0")))).isInstanceOf(IllegalArgumentException.class);
            assertThat(chat.isShowUserName()).isTrue();
            assertThat(chat.getChatCacheSize()).isEqualTo(256);
            assertThat(node.get("chat.show.username", null)).isNull();
            assertThat(node.get("chat.cache.size", null)).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"cache", "opacity", "group", "font-null", "theme-null"})
    void invalidLocalChangesAreRejectedBeforePublicationOrPersistence(String field, FxRobot robot) {
        robot.interact(() -> {
            var node = new MemoryPreferences();
            var prefs = new AppPreferences(node);
            var published = new java.util.concurrent.atomic.AtomicInteger();
            var chat = prefs.getChatPreferences();
            var state = prefs.getStatePreferences();
            chat.chatCacheSizeProperty().addListener((observable, old, value) -> published.incrementAndGet());
            chat.floatingChatOpacityProperty().addListener((observable, old, value) -> published.incrementAndGet());
            state.groupOrderItemProperty().addListener((observable, old, value) -> published.incrementAndGet());
            chat.fontProperty().addListener((observable, old, value) -> published.incrementAndGet());
            var general = prefs.getGeneralPreferences();
            var previousTheme = general.getTheme();
            general.themeProperty().addListener((observable, old, value) -> published.incrementAndGet());
            Runnable change = switch (field) {
                case "cache" -> () -> chat.setChatCacheSize(0);
                case "opacity" -> () -> chat.setFloatingChatOpacity(Double.NaN);
                case "group" -> () -> state.setGroupOrderItem("UNKNOWN");
                case "font-null" -> () -> chat.setFont(null);
                case "theme-null" -> () -> general.setTheme(null);
                default -> throw new AssertionError();
            };
            assertThatThrownBy(change::run).isInstanceOf(IllegalArgumentException.class);
            assertThat(chat.getChatCacheSize()).isEqualTo(256);
            assertThat(chat.getFloatingChatOpacity()).isEqualTo(0.7);
            assertThat(state.getGroupOrderItem()).isEqualTo("NAME");
            assertThat(chat.getFont()).isNotNull();
            assertThat(general.getTheme()).isEqualTo(previousTheme);
            assertThat(published).hasValue(0);
            assertThat(node.get("chat.cache.size", null)).isNull();
            assertThat(node.get("floatable.chat.opacity", null)).isNull();
            assertThat(node.get("group.order.item", null)).isNull();
        });
    }
}
