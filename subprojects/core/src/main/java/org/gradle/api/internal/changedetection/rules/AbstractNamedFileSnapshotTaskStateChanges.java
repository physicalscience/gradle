/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.rules;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.FilesSnapshotSet;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.util.ChangeListener;
import org.gradle.util.DiffUtil;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

abstract class AbstractNamedFileSnapshotTaskStateChanges implements TaskStateChanges, FilesSnapshotSet {
    private Map<String, FileCollectionSnapshot> fileSnapshotsBeforeExecution;
    private final String taskName;
    private final boolean allowSnapshotReuse;
    private final String title;
    protected final SortedSet<TaskFilePropertySpec> fileProperties;
    private final FileCollectionSnapshotter snapshotter;
    protected final TaskExecution previous;
    protected final TaskExecution current;

    protected AbstractNamedFileSnapshotTaskStateChanges(String taskName, TaskExecution previous, TaskExecution current, FileCollectionSnapshotter snapshotter, boolean allowSnapshotReuse, String title, SortedSet<TaskFilePropertySpec> fileProperties) {
        this.taskName = taskName;
        this.previous = previous;
        this.current = current;
        this.snapshotter = snapshotter;
        this.allowSnapshotReuse = allowSnapshotReuse;
        this.title = title;
        this.fileProperties = fileProperties;
        this.fileSnapshotsBeforeExecution = buildSnapshots(taskName, snapshotter, title, fileProperties, allowSnapshotReuse);
    }

    protected String getTaskName() {
        return taskName;
    }

    protected boolean isAllowSnapshotReuse() {
        return allowSnapshotReuse;
    }

    protected String getTitle() {
        return title;
    }

    protected SortedSet<TaskFilePropertySpec> getFileProperties() {
        return fileProperties;
    }

    protected abstract Set<FileCollectionSnapshot.ChangeFilter> getFileChangeFilters();
    protected abstract Map<String, FileCollectionSnapshot> getPrevious();

    protected abstract void saveCurrent();

    protected FileCollectionSnapshotter getSnapshotter() {
        return snapshotter;
    }

    protected Map<String, FileCollectionSnapshot> getCurrent() {
        return fileSnapshotsBeforeExecution;
    }

    protected static Map<String, FileCollectionSnapshot> buildSnapshots(String taskName, FileCollectionSnapshotter snapshotter, String title, SortedSet<TaskFilePropertySpec> fileProperties, boolean allowSnapshotReuse) {
        ImmutableMap.Builder<String, FileCollectionSnapshot> builder = ImmutableMap.builder();
        for (TaskFilePropertySpec propertySpec : fileProperties) {
            FileCollectionSnapshot result;
            try {
                result = snapshotter.snapshot(propertySpec.getPropertyFiles(), allowSnapshotReuse);
            } catch (UncheckedIOException e) {
                throw new UncheckedIOException(String.format("Failed to capture snapshot of %s files for task '%s' property '%s' during up-to-date check.", title.toLowerCase(), taskName, propertySpec.getPropertyName()), e);
            }
            builder.put(propertySpec.getPropertyName(), result);
        }
        return builder.build();
    }

    @Override
    public Iterator<TaskStateChange> iterator() {
        if (getPrevious() == null) {
            return Iterators.<TaskStateChange>singletonIterator(new DescriptiveChange(title + " file history is not available."));
        }
        final List<TaskStateChange> propertyChanges = Lists.newLinkedList();
        DiffUtil.diff(getCurrent().keySet(), getPrevious().keySet(), new ChangeListener<String>() {
            @Override
            public void added(String element) {
                propertyChanges.add(new DescriptiveChange("%s property '%s' has been added for task '%s'", title, element, taskName));
            }

            @Override
            public void removed(String element) {
                propertyChanges.add(new DescriptiveChange("%s property '%s' has been removed for task '%s'", title, element, taskName));
            }

            @Override
            public void changed(String element) {
                // Won't happen
                throw new AssertionError();
            }
        });
        if (!propertyChanges.isEmpty()) {
            return propertyChanges.iterator();
        }
        return Iterators.concat(Iterables.transform(getCurrent().entrySet(), new Function<Map.Entry<String, FileCollectionSnapshot>, Iterator<TaskStateChange>>() {
            @Override
            public Iterator<TaskStateChange> apply(Map.Entry<String, FileCollectionSnapshot> entry) {
                String propertyName = entry.getKey();
                FileCollectionSnapshot currentSnapshot = entry.getValue();
                FileCollectionSnapshot previousSnapshot = getPrevious().get(propertyName);
                String propertyTitle = title + " property '" + propertyName + "'";
                return currentSnapshot.iterateContentChangesSince(previousSnapshot, propertyTitle, getFileChangeFilters());
            }
        }).iterator());
    }

    @Override
    public void snapshotAfterTask() {
        saveCurrent();
    }

    public FilesSnapshotSet getUnifiedSnapshot() {
        return this;
    }

    @Nullable
    @Override
    public FileSnapshot findSnapshot(File file) {
        for (FileCollectionSnapshot propertySnapshot : getCurrent().values()) {
            FileSnapshot snapshot = propertySnapshot.getSnapshot().findSnapshot(file);
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }
}
