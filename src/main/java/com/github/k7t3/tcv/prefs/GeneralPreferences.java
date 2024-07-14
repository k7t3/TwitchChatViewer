package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.core.OS;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.app.theme.ThemeType;
import com.github.k7t3.tcv.database.DatabaseVersion;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;
import java.util.prefs.Preferences;

public class GeneralPreferences extends PreferencesBase {

    /** このアプリケーションに適用されるテーマの名称*/
    private static final String THEME = "theme";
    /** テーマのタイプ*/
    private static final String THEME_TYPE = "theme.type";

    /** チャンネルを複数選択して開くときの動作*/
    private static final String MULTIPLE_OPEN_TYPE = "multiple.open.type";

    /** ユーザーデータを格納するファイルのパス*/
    private static final String USER_DATA_FILE_PATH = "user.data.file";

    /**
     * データベースを構成するためのバージョン。
     * バージョンが異なるときはテーブルを変更する必要があるため現在の値を記録。
     */
    private static final String DATABASE_VERSION = "database.version";

    private static final String USER_DATA_FILE_NAME = "userdata.db";

    private ObjectProperty<Theme> theme;
    private ObjectProperty<ThemeType> themeType;

    private ObjectProperty<MultipleChatOpenType> multipleOpenType;

    private StringProperty userDataFilePath;

    GeneralPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(THEME, ThemeManager.DEFAULT_THEME.name());
        defaults.put(THEME_TYPE, ThemeType.SYSTEM.name());
        defaults.put(MULTIPLE_OPEN_TYPE, MultipleChatOpenType.SEPARATED.name());
        defaults.put(DATABASE_VERSION, DatabaseVersion.EMPTY.name());
        defaults.put(USER_DATA_FILE_PATH, OS.current().getApplicationDirectory()
                .toAbsolutePath()
                .resolve(USER_DATA_FILE_NAME)
                .toString());
    }

    public void setDatabaseVersion(DatabaseVersion version) {
        if (version == DatabaseVersion.EMPTY || version == DatabaseVersion.UNKNOWN) {
            throw new IllegalArgumentException();
        }
        preferences.put(DATABASE_VERSION, version.name());
    }

    public DatabaseVersion getDatabaseVersion() {
        var name = preferences.get(DATABASE_VERSION, (String) defaults.get(DATABASE_VERSION));
        return DatabaseVersion.valueOf(name);
    }

    @Override
    protected void readFromPreferences() {
        var themeName = get(THEME);
        if (themeName != null) {
            var theme = Theme.valueOf(themeName);
            setTheme(theme);
        }

        try {
            var type = MultipleChatOpenType.valueOf(get(MULTIPLE_OPEN_TYPE));
            if (getMultipleOpenType() != type) {
                setMultipleOpenType(type);
            }
        } catch (IllegalArgumentException ignored) {
        }

        var userDataFilePath = get(USER_DATA_FILE_PATH);
        if (!userDataFilePath.equalsIgnoreCase(getUserDataFilePath())) {
            setUserDataFilePath(userDataFilePath);
        }

        var databaseVersion = DatabaseVersion.valueOf(get(DATABASE_VERSION));
        if (databaseVersion != getDatabaseVersion()) {
            setDatabaseVersion(databaseVersion);
        }
    }

    @Override
    protected void writeToPreferences() {
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<Theme> themeProperty() {
        if (theme == null) {
            theme = createObjectProperty(THEME, Theme::valueOf, Theme::name);
        }
        return theme;
    }
    public Theme getTheme() { return themeProperty().get(); }
    public void setTheme(Theme theme) { themeProperty().set(theme); }

    public ObjectProperty<ThemeType> themeTypeProperty() {
        if (themeType == null) {
            themeType = createObjectProperty(THEME_TYPE, ThemeType::valueOf, ThemeType::name);
        }
        return themeType;
    }
    public ThemeType getThemeType() { return themeTypeProperty().get(); }
    public void setThemeType(ThemeType themeType) { themeTypeProperty().set(themeType); }

    public ObjectProperty<MultipleChatOpenType> multipleOpenTypeProperty() {
        if (multipleOpenType == null) {
            multipleOpenType = createObjectProperty(
                    MULTIPLE_OPEN_TYPE,
                    MultipleChatOpenType::valueOf,
                    Enum::name);
        }
        return multipleOpenType;
    }
    public MultipleChatOpenType getMultipleOpenType() { return multipleOpenTypeProperty().get(); }
    public void setMultipleOpenType(MultipleChatOpenType multipleOpenType) { multipleOpenTypeProperty().set(multipleOpenType); }

    public StringProperty userDataFilePathProperty() {
        if (userDataFilePath == null) userDataFilePath = createStringProperty(USER_DATA_FILE_PATH);
        return userDataFilePath;
    }
    public String getUserDataFilePath() { return userDataFilePathProperty().get(); }
    public void setUserDataFilePath(String userDataFilePath) { userDataFilePathProperty().set(userDataFilePath); }

}
