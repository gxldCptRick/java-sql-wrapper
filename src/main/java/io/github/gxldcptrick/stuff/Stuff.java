package io.github.gxldcptrick.stuff;

import io.github.gxldcptrick.sql.meta.ObjectId;
import io.github.gxldcptrick.sql.meta.ReferenceTo;

public class Stuff implements ObjectId {
    private int id;
    @ReferenceTo(TypeRefrenced = Flight.class, TableName = "Flights")
    private int stuffId;

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }


}
