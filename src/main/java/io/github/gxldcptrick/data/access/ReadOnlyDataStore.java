package io.github.gxldcptrick.data.access;

import java.util.stream.Stream;

public interface ReadOnlyDataStore<T> {
    T getRecord(int id);
    Stream<T> getRecords();
}
