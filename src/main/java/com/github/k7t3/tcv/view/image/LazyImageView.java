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

package com.github.k7t3.tcv.view.image;

import com.github.k7t3.tcv.app.image.LazyImage;
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
