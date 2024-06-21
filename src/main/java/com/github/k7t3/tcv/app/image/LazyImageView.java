package com.github.k7t3.tcv.app.image;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.scene.image.ImageView;

public class LazyImageView extends ImageView {

    public LazyImageView() {
    }

    public LazyImageView(LazyImage image) {
        setLazyImage(image);
    }

    private ObjectProperty<LazyImage> lazyImage = null;

    public ObjectProperty<LazyImage> lazyImageProperty() {
        if (lazyImage == null) {
            lazyImage = new ObjectPropertyBase<>() {

                @Override
                protected void invalidated() {
                    var lazyImage = get();
                    if (lazyImage != null) {
                        imageProperty().bind(lazyImage.valueProperty());
                    } else {
                        imageProperty().unbind();
                        setImage(null);
                    }
                }

                @Override
                public Object getBean() {
                    return this;
                }

                @Override
                public String getName() {
                    return "lazyImage";
                }
            };
        }
        return lazyImage;
    }

    public LazyImage getLazyImage() { return lazyImage == null ? null : lazyImage.get(); }

    public void setLazyImage(LazyImage lazyImage) { lazyImageProperty().set(lazyImage); }
}
