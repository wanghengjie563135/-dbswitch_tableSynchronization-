// Copyright tang.  All rights reserved.
// https://gitee.com/inrgihc/dbswitch
//
// Use of this source code is governed by a BSD-style license
//
// Author: tang (inrgihc@126.com)
// Date : 2020/1/2
// Location: beijing , china
/////////////////////////////////////////////////////////////
package com.gitee.dbswitch.core.database.impl;

import com.gitee.dbswitch.common.constant.Const;
import com.gitee.dbswitch.common.type.DatabaseTypeEnum;
import com.gitee.dbswitch.common.util.HivePrepareUtils;
import com.gitee.dbswitch.core.database.AbstractDatabase;
import com.gitee.dbswitch.core.database.IDatabaseInterface;
import com.gitee.dbswitch.core.model.ColumnDescription;
import com.gitee.dbswitch.core.model.ColumnMetaData;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DatabaseHiveImpl extends AbstractDatabase implements IDatabaseInterface {

  private static final String SHOW_CREATE_TABLE_SQL = "SHOW CREATE TABLE `%s`.`%s` ";

  public DatabaseHiveImpl() {
    super("org.apache.hive.jdbc.HiveDriver");
  }

  @Override
  public DatabaseTypeEnum getDatabaseType() {
    return DatabaseTypeEnum.HIVE;
  }

  @Override
  public String getTableDDL(Connection connection, String schemaName, String tableName) {
    String sql = String.format(SHOW_CREATE_TABLE_SQL, schemaName, tableName);
    List<String> result = new ArrayList<>();
    try (Statement st = connection.createStatement()) {
      if (st.execute(sql)) {
        try (ResultSet rs = st.getResultSet()) {
          if (rs != null) {
            while (rs.next()) {
              String value = rs.getString(1);
              Optional.ofNullable(value).ifPresent(result::add);
            }
          }
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    return String.join("\n", result);
  }

  @Override
  public String getViewDDL(Connection connection, String schemaName, String tableName) {
    return getTableDDL(connection, schemaName, tableName);
  }

  @Override
  public List<ColumnDescription> queryTableColumnMeta(Connection connection, String schemaName,
      String tableName) {
    String querySQL = this.getTableFieldsQuerySQL(schemaName, tableName);
    List<ColumnDescription> ret = new ArrayList<>();
    try (Statement st = connection.createStatement()) {
      HivePrepareUtils.prepare(connection, schemaName, tableName);
      try (ResultSet rs = st.executeQuery(querySQL)) {
        ResultSetMetaData m = rs.getMetaData();
        int columns = m.getColumnCount();
        for (int i = 1; i <= columns; i++) {
          String name = m.getColumnLabel(i);
          if (null == name) {
            name = m.getColumnName(i);
          }

          ColumnDescription cd = new ColumnDescription();
          cd.setFieldName(name);
          cd.setLabelName(name);
          cd.setFieldType(m.getColumnType(i));
          if (0 != cd.getFieldType()) {
            cd.setFieldTypeName(m.getColumnTypeName(i));
            cd.setFiledTypeClassName(m.getColumnClassName(i));
            cd.setDisplaySize(m.getColumnDisplaySize(i));
            cd.setPrecisionSize(m.getPrecision(i));
            cd.setScaleSize(m.getScale(i));
            cd.setAutoIncrement(m.isAutoIncrement(i));
            cd.setNullable(m.isNullable(i) != ResultSetMetaData.columnNoNulls);
          } else {
            // 处理视图中NULL as fieldName的情况
            cd.setFieldTypeName("CHAR");
            cd.setFiledTypeClassName(String.class.getName());
            cd.setDisplaySize(1);
            cd.setPrecisionSize(1);
            cd.setScaleSize(0);
            cd.setAutoIncrement(false);
            cd.setNullable(true);
          }

          boolean signed = false;
          try {
            signed = m.isSigned(i);
          } catch (Exception ignored) {
            // This JDBC Driver doesn't support the isSigned method
            // nothing more we can do here by catch the exception.
          }
          cd.setSigned(signed);
          cd.setDbType(DatabaseTypeEnum.HIVE);

          ret.add(cd);
        }

        return ret;
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<String> queryTablePrimaryKeys(Connection connection, String schemaName,
      String tableName) {
    return Collections.emptyList();
  }

  @Override
  public List<ColumnDescription> querySelectSqlColumnMeta(Connection connection, String sql) {
    String querySQL = String.format(" %s LIMIT 1", sql.replace(";", ""));
    return this.getSelectSqlColumnMeta(connection, querySQL);
  }

  @Override
  protected String getTableFieldsQuerySQL(String schemaName, String tableName) {
    return String.format("SELECT * FROM `%s`.`%s` ", schemaName, tableName);
  }

  @Override
  protected String getTestQuerySQL(String sql) {
    return String.format("explain %s", sql.replace(";", ""));
  }

  @Override
  public String getQuotedSchemaTableCombination(String schemaName, String tableName) {
    return String.format("  `%s`.`%s` ", schemaName, tableName);
  }

  @Override
  public String getFieldDefinition(ColumnMetaData v, List<String> pks, boolean useAutoInc,
      boolean addCr) {
    String fieldname = v.getName();
    int type = v.getType();

    String retval = " `" + fieldname + "`  ";

    switch (type) {
      case ColumnMetaData.TYPE_TIMESTAMP:
      case ColumnMetaData.TYPE_TIME:
        retval += "TIMESTAMP";
        break;
      case ColumnMetaData.TYPE_DATE:
        retval += "DATE";
        break;
      case ColumnMetaData.TYPE_BOOLEAN:
        retval += "TINYINT";
        break;
      case ColumnMetaData.TYPE_NUMBER:
        retval += "FLOAT";
        break;
      case ColumnMetaData.TYPE_INTEGER:
        retval += "DECIMAL";
        break;
      case ColumnMetaData.TYPE_BIGNUMBER:
        retval += "BIGINT";
        break;
      case ColumnMetaData.TYPE_STRING:
        retval += "STRING";
        break;
      case ColumnMetaData.TYPE_BINARY:
        retval += "BINARY";
        break;
      default:
        retval += "STRING";
        break;
    }

    if (addCr) {
      retval += Const.CR;
    }

    return retval;
  }

}
