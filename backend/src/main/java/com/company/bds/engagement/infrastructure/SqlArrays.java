package com.company.bds.engagement.infrastructure;

import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.support.AbstractSqlTypeValue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

/** Binds Java collections as PostgreSQL arrays ({@code = ANY(?)}). */
final class SqlArrays {
    private SqlArrays() {}

    static SqlTypeValue uuids(Collection<?> values) {
        return array("uuid", values.toArray());
    }

    static SqlTypeValue texts(Collection<String> values) {
        return array("text", values.toArray());
    }

    private static SqlTypeValue array(String type, Object[] values) {
        return new AbstractSqlTypeValue() {
            @Override
            protected Object createTypeValue(Connection connection, int sqlType, String typeName) throws SQLException {
                return connection.createArrayOf(type, values);
            }
        };
    }
}
