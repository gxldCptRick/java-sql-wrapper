package io.github.gxldcptrick.sql;

import io.github.gxldcptrick.security.LoginInfo;
import io.github.gxldcptrick.sql.meta.ObjectId;

import java.sql.Connection;
import java.util.Map;

public class SqlDatabaseWrapper {
    private Map<String, SqlTableWrapper<?>> tables;
    private Connection connection;
    public SqlDatabaseWrapper(String connectionString, LoginInfo dbLogin){

    }
    public void addTable(String tableName, Class<? extends ObjectId> type){
    	
    }
}
