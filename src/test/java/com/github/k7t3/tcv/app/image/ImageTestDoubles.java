package com.github.k7t3.tcv.app.image;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ImageTestDoubles {
    private ImageTestDoubles() {
    }

    public static ImageLoader emptyLoader() {
        var images = mock(ImageLoader.class);
        when(images.load(anyString(), anyDouble(), anyDouble())).thenAnswer(invocation -> {
            var image = mock(LazyImage.class);
            when(image.valueProperty()).thenReturn(new ReadOnlyObjectWrapper<Image>().getReadOnlyProperty());
            return image;
        });
        return images;
    }
}
