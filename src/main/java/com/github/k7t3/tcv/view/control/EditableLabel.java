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

package com.github.k7t3.tcv.view.control;

import javafx.beans.property.*;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.io.Serial;

/**
 * 編集可能なラベルコントロール
 * <p>
 *     デフォルトではラベルが表示されており、ラベルをクリックすると編集用の
 *     {@link javafx.scene.control.TextField}コントロールに切り替わる。
 * </p>
 * <li>{@link #setOnEditStart(EventHandler)}</li>
 * <li>{@link #setOnEditCommit(EventHandler)}</li>
 * <li>{@link #setOnEditCancel(EventHandler)}</li>
 * @see javafx.scene.control.ListView
 */
@SuppressWarnings("unused")
public class EditableLabel extends Control {

    /* *************************************************************************
     *                                                                         *
     * Static properties and methods                                           *
     *                                                                         *
     **************************************************************************/

    private static final String STYLE_CLASS = "editable-label";

    /**
     * An EventType that indicates some edit event has occurred. It is the parent
     * type of all other edit events: {@link #editStartEvent()},
     *  {@link #editCommitEvent()} and {@link #editCancelEvent()}.
     * @return the event type
     */
    @SuppressWarnings("unchecked")
    public static  EventType<EditEvent> editAnyEvent() {
        return (EventType<EditEvent>) EDIT_ANY_EVENT;
    }
    private static final EventType<?> EDIT_ANY_EVENT =
            new EventType<>(Event.ANY, "EDITABLE_LABEL_EDIT");

    /**
     * An EventType used to indicate that an edit event has started within the
     * ListView upon which the event was fired.
     * @return the event type
     */
    @SuppressWarnings("unchecked")
    public static  EventType<EditEvent> editStartEvent() {
        return (EventType<EditEvent>) EDIT_START_EVENT;
    }
    private static final EventType<?> EDIT_START_EVENT =
            new EventType<>(editAnyEvent(), "EDITABLE_LABEL_EDIT_START");

    /**
     * An EventType used to indicate that an edit event has just been canceled
     * within the ListView upon which the event was fired.
     * @return the event type
     */
    @SuppressWarnings("unchecked")
    public static  EventType<EditEvent> editCancelEvent() {
        return (EventType<EditEvent>) EDIT_CANCEL_EVENT;
    }
    private static final EventType<?> EDIT_CANCEL_EVENT =
            new EventType<>(editAnyEvent(), "EDITABLE_LABEL_EDIT_CANCEL");

    /**
     * An EventType used to indicate that an edit event has been committed
     * within the ListView upon which the event was fired.
     * @return the event type
     */
    @SuppressWarnings("unchecked")
    public static  EventType<EditEvent> editCommitEvent() {
        return (EventType<EditEvent>) EDIT_COMMIT_EVENT;
    }
    private static final EventType<?> EDIT_COMMIT_EVENT =
            new EventType<>(editAnyEvent(), "EDITABLE_LABEL_EDIT_COMMIT");

    /* *************************************************************************
     *                                                                         *
     * Fields                                                                  *
     *                                                                         *
     **************************************************************************/

    private final StringProperty text = new SimpleStringProperty("", "text");

    private final ReadOnlyBooleanWrapper editing = new ReadOnlyBooleanWrapper(false, "editing");

    private final StringProperty promptText = new SimpleStringProperty("", "promptText");

    public EditableLabel() {
        getStyleClass().add(STYLE_CLASS);
    }

    public EditableLabel(String text) {
        this();
        setText(text);
    }

    public StringProperty textProperty() { return text; }
    public String getText() { return text.get(); }
    public void setText(String text) { this.text.set(text); }

    public ReadOnlyBooleanProperty editingProperty() { return editing.getReadOnlyProperty(); }
    public boolean isEditing() { return editing.get(); }
    void setEditing(boolean editing) { this.editing.set(editing); }

    public StringProperty promptTextProperty() { return promptText; }
    public String getPromptText() { return promptText.get(); }
    public void setPromptText(String promptText) { this.promptText.set(promptText); }



    private ObjectProperty<EventHandler<EditEvent>> onEditStart;

    public final ObjectProperty<EventHandler<EditEvent>> onEditStartProperty() {
        if (onEditStart == null) {
            onEditStart = new ObjectPropertyBase<>() {
                @Override protected void invalidated() {
                    setEventHandler(EditableLabel.editStartEvent(), get());
                }

                @Override
                public Object getBean() {
                    return EditableLabel.this;
                }

                @Override
                public String getName() {
                    return "onEditStart";
                }
            };
        }
        return onEditStart;
    }
    public final EventHandler<EditEvent> getOnEditStart() { return onEditStart == null ? null : onEditStart.get(); }
    public final void setOnEditStart(EventHandler<EditEvent> value) { onEditStartProperty().set(value); }


    private ObjectProperty<EventHandler<EditEvent>> onEditCommit;

    public final ObjectProperty<EventHandler<EditEvent>> onEditCommitProperty() {
        if (onEditCommit == null) {
            onEditCommit = new ObjectPropertyBase<>() {
                @Override protected void invalidated() {
                    setEventHandler(EditableLabel.editCommitEvent(), get());
                }

                @Override
                public Object getBean() {
                    return EditableLabel.this;
                }

                @Override
                public String getName() {
                    return "onEditCommit";
                }
            };
        }
        return onEditCommit;
    }
    public final void setOnEditCommit(EventHandler<EditEvent> value) { onEditCommitProperty().set(value); }
    public final EventHandler<EditEvent> getOnEditCommit() { return onEditCommit == null ? null : onEditCommit.get(); }


    private ObjectProperty<EventHandler<EditEvent>> onEditCancel;

    public final ObjectProperty<EventHandler<EditEvent>> onEditCancelProperty() {
        if (onEditCancel == null) {
            onEditCancel = new ObjectPropertyBase<>() {
                @Override protected void invalidated() {
                    setEventHandler(EditableLabel.editCancelEvent(), get());
                }

                @Override
                public Object getBean() {
                    return EditableLabel.this;
                }

                @Override
                public String getName() {
                    return "onEditCancel";
                }
            };
        }
        return onEditCancel;
    }
    public final void setOnEditCancel(EventHandler<EditEvent> value) { onEditCancelProperty().set(value); }
    public final EventHandler<EditEvent> getOnEditCancel() { return onEditCancel == null ? null : onEditCancel.get(); }


    @Override
    protected Skin<?> createDefaultSkin() {
        return new EditableLabelSkin(this);
    }

    @Override
    public String toString() {
        return "EditableLabel{" +
                "text=" + getText() +
                '}';
    }

    public static class EditEvent extends Event {
        private final String newValue;
        private final EditableLabel source;

        @Serial
        private static final long serialVersionUID = -9128821744906281990L;

        /**
         * Common supertype for all edit event types.
         * @since JavaFX 8.0
         */
        public static final EventType<?> ANY = EDIT_ANY_EVENT;

        /**
         * Creates a new EditEvent instance to represent an edit event. This
         * event is used for {@link #editStartEvent()},
         * {@link #editCommitEvent()} and {@link #editCancelEvent()} types.
         * @param source the source
         * @param eventType the event type
         * @param newValue the new value
         */
        public EditEvent(EditableLabel source,
                         EventType<? extends EditEvent> eventType,
                         String newValue) {
            super(source, Event.NULL_SOURCE_TARGET, eventType);
            this.source = source;
            this.newValue = newValue;
        }

        /**
         * Returns the ListView upon which the edit took place.
         */
        @Override public EditableLabel getSource() {
            return source;
        }

        /**
         * Returns the value of the new input provided by the end user.
         * @return the value of the new input provided by the end user
         */
        public String getNewValue() {
            return newValue;
        }


        @Override
        public String toString() {
            return "EditEvent{" +
                    "newValue='" + newValue + '\'' +
                    ", source=" + source +
                    ", eventType=" + eventType +
                    '}';
        }
    }

}
