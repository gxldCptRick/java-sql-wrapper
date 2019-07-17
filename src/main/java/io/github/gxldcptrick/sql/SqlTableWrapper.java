package io.github.gxldcptrick.sql;

import io.github.gxldcptrick.sql.meta.ExcludeField;
import io.github.gxldcptrick.sql.meta.ObjectId;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SqlTableWrapper<T extends ObjectId> {
    private Connection connection;
    private String tableName;
    private String insertTemplate;
    private String updateTemplate;
    private String deleteTemplate;
    private String selectTemplate;
    private String selectSomeTemplate;
    private String selectOneTemplate;
    private String joinOnIDTemplate;
    private Class<? extends ObjectId> typeInfo;
    private String selectSpecificTemplate;
    private String countTemplate;

    public SqlTableWrapper(Class<? extends ObjectId> typeInfo, Connection connection, String tableName) {
        setConnection(connection);
        setTableName(tableName);
        createTemplates(typeInfo);
        this.typeInfo = typeInfo;
    }

    private static String capitalizeString(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    private Stream<Field> getFields(Class<?> type) {
        var fields = type.getDeclaredFields();
        var stream = Arrays.stream(fields);
        return stream.filter(f -> f.getAnnotation(ExcludeField.class) == null);
    }

    private void createTemplates(Class<?> typeInfo) {
        //crafting the inserting template
        var props = new StringBuilder("(");
        var propNames = new StringBuilder();
        var values = new StringBuilder("(");
        var firstPassed = false;
        var listOPropNames = getFields(typeInfo)
                .filter(f -> !f.getName().equalsIgnoreCase("id"))
                .map(Field::getName)
                .collect(Collectors.toList());
        for (var propName : listOPropNames) {
            propNames.append(firstPassed ? "," : "").append(propName);
            values.append(firstPassed ? "," : "").append("?");
            firstPassed = true;
        }
        props.append(propNames.toString());
        props.append(")");
        values.append(")");
        insertTemplate = String.format("INSERT INTO %s %s VALUES %s", getTableName(), props, values);

        //crafting the update template im actually going to cry out of pain.. WHY GOD WHY!!
        var updateBuilder = new StringBuilder(String.format("UPDATE %s SET ", getTableName()));
        var settingBuilder = new StringBuilder();
        firstPassed = false;
        for (var propName : listOPropNames) {
            settingBuilder.append(firstPassed ? "," : "").append(propName).append("=? ");
            firstPassed = true;
        }
        updateBuilder.append(settingBuilder);
        updateBuilder.append("WHERE id=?");
        updateTemplate = updateBuilder.toString();


        //crafting the delete statement from scratch hardest one to do yet oof big oof biggest oof
        deleteTemplate = String.format("DELETE FROM %s WHERE id = ?", getTableName());

        propNames.append(", id");

        //crafting the simplest of them all the select all.
        var propNameString = propNames.toString();
        selectTemplate = String.format("SELECT %s FROM %s", propNameString, getTableName());

        //crafting the complex select some template no joke this was actually tricky to get right.
        var subQuery = String.format("SELECT TOP(?) id FROM %s ORDER BY id", getTableName());
        selectSomeTemplate = String.format("SELECT TOP(?) %s FROM %s WHERE id NOT IN (%s) ORDER BY id", propNameString, getTableName(), subQuery);

        //crafting the simple select one query so that we can get a single record out.
        selectOneTemplate = String.format("Select %s FROM %s WHERE id=?", propNameString, getTableName());

        //crafting the specific query so that we can find a contact in the database with the name email etc..
        selectSpecificTemplate = String.format("SELECT %s FROM %s WHERE %s", propNames, getTableName(), settingBuilder.toString());

        //crafting the template so that we can count indexes in a table.
        countTemplate = String.format("SELECT COUNT(id) FROM %s", getTableName());

        var annotation = getFields(typeInfo);
        joinOnIDTemplate = String.format("SELECT %s FROM %s as %s1 INNER JOIN %s as %s2 ON %s1.%s = %s2.id", propNameString, getTableName(), getTableName() );
        /*
         * Base join statement:
         * SELECT %s FROM %s as %s1
         * INNER JOIN %s as %s2
         * ON %s1.COLUMN = %s2.id
         */
    }

    private void setConnection(Connection connection) {
        this.connection = connection;
    }

    public String getTableName() {
        return tableName;
    }

    private void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void create(T instance) throws SQLException {
        Objects.requireNonNull(instance);
        var classOfInstance = instance.getClass();
        var fields = getFields(classOfInstance).filter(f -> !f.getName().equalsIgnoreCase("id")).collect(Collectors.toList());
        try (var preparedStatement = connection.prepareStatement(insertTemplate, Statement.RETURN_GENERATED_KEYS)) {
            for (var i = 0; i < fields.size(); i++) {
                prepareStatementPropertyAtGivenIndex(i + 1, fields.get(i), instance, preparedStatement);
            }
            preparedStatement.executeUpdate();
            try (var results = preparedStatement.getGeneratedKeys()) {
                results.next();
                instance.setId(results.getInt(1));
            }
        }
    }

    public void update(int id, T instance) throws SQLException {
        var fields = getFields(instance.getClass()).filter(f -> !f.getName().equalsIgnoreCase("id")).collect(Collectors.toList());
        try (var preparedStatement = connection.prepareStatement(updateTemplate)) {
            for (var i = 0; i < fields.size(); i++) {
                prepareStatementPropertyAtGivenIndex(i + 1, fields.get(i), instance, preparedStatement);
            }
            preparedStatement.setInt(fields.size() + 1, id);
            preparedStatement.execute();
        }
    }

    public void delete(int id) throws SQLException {
        try (var prepareStatement = connection.prepareStatement(deleteTemplate)) {
            prepareStatement.setInt(1, id);
            prepareStatement.execute();
        }
    }

    public Stream<T> getAll() throws SQLException {
        var builder = Stream.<T>builder();
        try (var preparedStatement = connection.prepareStatement(selectTemplate)) {
            try (var query = preparedStatement.executeQuery()) {
                while (query.next()) {
                    builder.accept(convertRowToObject(query));
                }
            }
        }
        return builder.build();
    }

    public Stream<T> getRange(int offset, int amountToTake) throws SQLException {
        var builder = Stream.<T>builder();
        try (var preparedStatement = connection.prepareStatement(selectSomeTemplate)) {
            preparedStatement.setInt(1, amountToTake);
            preparedStatement.setInt(2, offset);
            try (var query = preparedStatement.executeQuery()) {
                while (query.next()) {
                    builder.accept(convertRowToObject(query));
                }
            }
        }
        return builder.build();
    }

    public T getById(int id) throws SQLException{
        T result = null;
        try(var preparedStatement = connection.prepareStatement(selectOneTemplate)){
            preparedStatement.setInt(1, id);
            try(var query = preparedStatement.executeQuery()){
                while(query.next()){
                    if(result == null){
                        result = convertRowToObject(query);
                    }else{
                        throw new IllegalArgumentException("There was more than one record returned");
                    }
                }
            }
        }
        return result;
    }

    private void prepareStatementPropertyAtGivenIndex(int index, Field field, T instance, PreparedStatement preparedStatement) throws SQLException {
        var type = field.getType();
        var getterMethod = getMethodFromInstance(instance, field, "get");
        var value = invokeGetterOnInstance(instance, getterMethod);
        if (type.equals(int.class)) {
            preparedStatement.setInt(index, (int) value);
        } else if (type.equals(String.class)) {
            preparedStatement.setString(index, (String) value);
        } else {
            throw new IllegalArgumentException("The Type cannot be easily converted to sql so.. FUCKING BAIL!!");
        }
    }

    private Object invokeGetterOnInstance(T instance, Method getterMethod) {
        Object value = null;
        try {
            value = getterMethod.invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return value;
    }

    private Method getMethodFromInstance(T instance, Field field, String getterOrSetter) {
        var getterName = getterOrSetter + capitalizeString(field.getName());
        var classOfInstance = instance.getClass();
        Method getter;
        try {
            getter = classOfInstance.getMethod(getterName, (getterOrSetter.equalsIgnoreCase("get") ? new Class<?>[0] : new Class<?>[]{field.getType()}));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find a " + getterOrSetter + " for: " + field.getName());
        }
        return getter;
    }

    private T convertRowToObject(ResultSet row) throws SQLException {
        var instance = createNewInstance();
        var fields = getFields(typeInfo).collect(Collectors.toList());
        for (var field : fields) {
            setValueToFieldFromRow(row, instance, field);
        }
        return instance;
    }

    private void setValueToFieldFromRow(ResultSet row, T instance, Field field) throws SQLException {
        var fieldType = field.getType();
        var method = getMethodFromInstance(instance, field, "set");
        if (fieldType.equals(int.class)) {
            try {
                method.invoke(instance, row.getInt(field.getName()));
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else if (fieldType.equals(String.class)) {
            try {
                method.invoke(instance, row.getString(field.getName()));
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else {
            throw new IllegalArgumentException("I don't know what you are so... BAIL FUCKING BAIL!!.");
        }
    }

    private T createNewInstance() {
        T crafted = null;
        try {
            crafted = (T) (typeInfo.getConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return crafted;
    }

    public int count() throws SQLException {
        var count  = 0;
        try(var statement = connection.prepareStatement(countTemplate)){
            try(var result = statement.executeQuery()){
                while(result.next()){
                    count = result.getInt(1);
                }
            }
        }
        return count;
    }

    public int getIdFor(T contact) throws SQLException {
        var id = -1;
        var fields = getFields(contact.getClass()).filter(f -> !f.getName().equalsIgnoreCase("id")).collect(Collectors.toList());
        try(var preparedStatement = connection.prepareStatement(this.selectSpecificTemplate)){
            for (int i = 0; i < fields.size(); i++) {
                prepareStatementPropertyAtGivenIndex(i, fields.get(i), contact, preparedStatement);
            }
            try(var query = preparedStatement.executeQuery()){
                if(query.next()){
                    var obj =  convertRowToObject(query);
                    id = obj.getId();
                }
            }
        }
        return id;
    }
}
