package com.github.k7t3.tcv.app.model;

import javafx.beans.property.*;

@SuppressWarnings("SimplifiableConditionalExpression")
public class EditableViewModelBase {

    private BooleanProperty removed;
    private ReadOnlyBooleanWrapper dirty;
    private ReadOnlyBooleanWrapper invalid;

    public EditableViewModelBase() {
    }

    private void markDirty() {
        dirtyWrapper().set(true);
    }

    private ReadOnlyBooleanWrapper dirtyWrapper() {
        if (dirty == null) {
            dirty = new ReadOnlyBooleanWrapper();
        }
        return dirty;
    }
    public ReadOnlyBooleanProperty dirtyProperty() { return dirtyWrapper().getReadOnlyProperty(); }
    public boolean isDirty() { return dirty == null ? false : dirty.get(); }

    private ReadOnlyBooleanWrapper invalidWrapper() {
        if (invalid == null) {
            invalid = new ReadOnlyBooleanWrapper();
        }
        return invalid;
    }
    public ReadOnlyBooleanProperty invalidProperty() { return invalidWrapper().getReadOnlyProperty(); }
    public boolean isInvalid() { return invalid == null ? false : invalid.get(); }
    protected void setInvalid(boolean invalid) { this.invalidWrapper().set(invalid); }

    public BooleanProperty removedProperty() {
        if (removed == null) {
            removed = new SimpleBooleanProperty() {
                @Override
                protected void invalidated() {
                    markDirty();
                }
            };
        }
        return removed;
    }
    public boolean isRemoved() { return removed.get(); }
    public void setRemoved(boolean removed) { this.removed.set(removed); }

    protected StringProperty stringProperty() {
        return new StringPropertyBase() {
            @Override
            public Object getBean() {
                return null;
            }

            @Override
            public String getName() {
                return "";
            }

            @Override
            protected void invalidated() {
                markDirty();
            }
        };
    }

    protected ReadOnlyStringWrapper readOnlyStringWrapper() {
        return new ReadOnlyStringWrapper() {
            @Override
            protected void invalidated() {
                markDirty();
            }
        };
    }

    protected BooleanProperty booleanProperty() {
        return new BooleanPropertyBase() {
            @Override
            public Object getBean() {
                return null;
            }

            @Override
            public String getName() {
                return "";
            }

            @Override
            protected void invalidated() {
                markDirty();
            }
        };
    }

    protected ReadOnlyBooleanWrapper readOnlyBooleanWrapper() {
        return new ReadOnlyBooleanWrapper() {
            @Override
            protected void invalidated() {
                markDirty();
            }
        };
    }

    protected <T> ObjectProperty<T> objectProperty() {
        return new ObjectPropertyBase<T>() {
            @Override
            public Object getBean() {
                return null;
            }

            @Override
            public String getName() {
                return "";
            }

            @Override
            protected void invalidated() {
                markDirty();
            }
        };
    }

    protected <T> ReadOnlyObjectWrapper<T> readOnlyObjectWrapper() {
        return new ReadOnlyObjectWrapper<>() {
            @Override
            protected void invalidated() {
                markDirty();
            }
        };
    }

}
