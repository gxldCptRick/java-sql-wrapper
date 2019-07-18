package io.github.gxldcptrick.sql;

import io.github.gxldcptrick.security.LoginInfo;
import io.github.gxldcptrick.sql.meta.ObjectId;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class SqlDatabaseWrapper implements AutoCloseable {
    private Map<String, SqlTableWrapper<?>> tables;
    private Connection connection;
    public SqlDatabaseWrapper(String connectionString, LoginInfo dbLogin){

    }
    public void addTable(String tableName, Class<? extends ObjectId> type){
<<<<<<< HEAD
    	
=======
    }

    @Override
    public void close() throws SQLException {
        connection.close();
>>>>>>> c6a4bd11756a186826700e4b2cd4ae247b163cb9
    }
}
