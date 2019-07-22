package io.github.gxldcptrick.data.access;

public interface DataStore<T>  extends ReadOnlyDataStore<T> {
    void addRecord(T data);
    void removeRecord(int id, T data);
    void updateRecord(int id, T data);
}
