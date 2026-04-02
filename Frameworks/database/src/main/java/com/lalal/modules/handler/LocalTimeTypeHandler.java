package com.lalal.modules.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.time.LocalTime;

public class LocalTimeTypeHandler extends BaseTypeHandler<LocalTime> {
    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int i, LocalTime localTime, JdbcType jdbcType) throws SQLException {
        preparedStatement.setTime(i, Time.valueOf(localTime));
    }

    @Override
    public LocalTime getNullableResult(ResultSet resultSet, String s) throws SQLException {
        Time sqlTime = resultSet.getTime(s);
        return sqlTime == null ? null : sqlTime.toLocalTime();
    }

    @Override
    public LocalTime getNullableResult(ResultSet resultSet, int i) throws SQLException {
        Time sqlTime = resultSet.getTime(i);
        return sqlTime == null ? null : sqlTime.toLocalTime();
    }

    @Override
    public LocalTime getNullableResult(CallableStatement callableStatement, int i) throws SQLException {
        Time sqlTime = callableStatement.getTime(i);
        return sqlTime == null ? null : sqlTime.toLocalTime();
    }
}
