package com.github.k7t3.tcv.prefs;

import atlantafx.base.theme.Theme;
import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.core.OS;
import com.github.k7t3.tcv.view.core.ThemeManager;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;
import java.util.prefs.Preferences;

public class GeneralPreferences extends PreferencesBase {

    /**
     * このアプリケーションに適用されるAtlantaFXのテーマの名称
     */
    private static final String THEME = "theme";

    /**
     * チャンネルを複数選択して開くときの動作
     */
    private static final String MULTIPLE_OPEN_TYPE = "multiple.open.type";

    /**
     * ユーザーデータを格納するファイルのパス
     */
    private static final String USER_DATA_FILE_PATH = "user.data.file";

    private static final String USER_DATA_FILE_NAME = "userdata.db";

    private ObjectProperty<MultipleChatOpenType> multipleOpenType;

    private StringProperty userDataFilePath;

    GeneralPreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(THEME, ThemeManager.DEFAULT_THEME.getName());
        defaults.put(MULTIPLE_OPEN_TYPE, MultipleChatOpenType.MERGED.name());
        defaults.put(USER_DATA_FILE_PATH, OS.current().getApplicationDirectory()
                .toAbsolutePath()
                .resolve(USER_DATA_FILE_NAME)
                .toString());
    }

    public void setTheme(Theme theme) {
        preferences.put(THEME, theme.getName());
    }

    public Theme getTheme() {
        var name = preferences.get(THEME, (String) defaults.get(THEME));
        return ThemeManager.getInstance().findTheme(name).orElseThrow();
    }

    @Override
    protected void readFromPreferences() {
        var themeName = get(THEME);
        if (themeName != null) {
            ThemeManager.getInstance().findTheme(themeName).ifPresent(this::setTheme);
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
    }

    @Override
    protected void writeToPreferences() {
    }

    // ******************** PROPERTIES ********************

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
