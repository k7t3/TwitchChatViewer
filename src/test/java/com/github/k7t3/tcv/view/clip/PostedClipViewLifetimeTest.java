package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import java.time.LocalDateTime;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class PostedClipViewLifetimeTest {
    @Test void closingTheViewDisconnectsTheSharedRepositoryAndClearsItsProjection(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(PostedClipRepository.class);
            var source = FXCollections.<String, PostedClipItem>observableHashMap();
            when(repository.getItems()).thenReturn(source); when(repository.getPostedBroadcasters()).thenReturn(List.of());
            var first = mock(PostedClipItem.class); when(first.getLastPostedAt()).thenReturn(LocalDateTime.now()); source.put("first", first);
            var controller = new PostedClipRepositoryView();
            var tuple = FluentViewLoader.fxmlView(PostedClipRepositoryView.class).codeBehind(controller).viewModel(repository)
                    .resourceBundle(Resources.getResourceBundle()).load();
            var list = (ListView<?>) tuple.getView().lookup("#videoClips"); var projection = list.getItems();
            assertThat(projection).hasSize(1);
            controller.close(); controller.close();
            var late = mock(PostedClipItem.class); when(late.getLastPostedAt()).thenReturn(LocalDateTime.now()); source.put("late", late);
            assertThat(projection).isEmpty(); assertThat(list.getItems()).isNull();
            verify(repository, never()).close(); assertThat(source).hasSize(2);
        });
    }
}
