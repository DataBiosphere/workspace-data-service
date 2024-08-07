package org.databiosphere.workspacedataservice.search;

import static org.databiosphere.workspacedataservice.dao.SqlUtils.quote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.flexible.core.QueryNodeParseException;
import org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.databiosphere.workspacedataservice.service.model.DataTypeMapping;

public class QueryParser {

  public static final String DEFAULT_ALL_COLUMNS_NAME = "sys_all_columns";

  private final Map<String, DataTypeMapping> schema;

  public QueryParser(Map<String, DataTypeMapping> schema) {
    this.schema = schema;
  }

  public WhereClausePart parse(String query) {
    // query should not be blank by the time we get here, but if it is, short-circuit and return
    // an empty clause.
    if (StringUtils.isBlank(query)) {
      return new WhereClausePart(List.of(), Map.of());
    }
    // create a Lucene parser
    StandardSyntaxParser standardSyntaxParser = new StandardSyntaxParser();

    // attempt to parse
    QueryNode parsed;
    try {
      parsed = standardSyntaxParser.parse(query, DEFAULT_ALL_COLUMNS_NAME);
    } catch (QueryNodeParseException queryNodeParseException) {
      throw new InvalidQueryException();
    }

    // even if we parsed correctly, ensure the query does not use any syntax we don't support
    if (parsed instanceof FieldQueryNode fieldQueryNode) {
      String column = fieldQueryNode.getFieldAsString();
      String value = fieldQueryNode.getTextAsString();

      validateColumnName(column);

      List<String> clauses = new ArrayList<>();
      Map<String, String> values = new HashMap<>();
      List<String> columns = new ArrayList<>();

      // bind parameter names have syntax limitations, so we use an artificial one
      var paramName = "filterquery0";
      clauses.add("LOWER(" + quote(column) + ") = :" + paramName);
      values.put(paramName, value.toLowerCase());

      return new WhereClausePart(clauses, values);
    } else {
      throw new InvalidQueryException();
    }
  }

  private void validateColumnName(String columnName) {
    // The Lucene query parser requires a default column name to parse a query. If the end user
    // has not specified a column, the query parser will use the default column name. In our case,
    // if we see the default column name we consider it an error - as of this writing, we require
    // the end user to specify a column name.
    if (DEFAULT_ALL_COLUMNS_NAME.equals(columnName)) {
      throw new InvalidQueryException("Query must specify a column name");
    }
    // does this column exist in the record type?
    if (!schema.containsKey(columnName)) {
      throw new InvalidQueryException(
          "Column specified in query does not exist in this record type");
    }
    // is this column of a datatype we currently support?
    var datatype = schema.get(columnName);
    if (!DataTypeMapping.STRING.equals(datatype)) {
      throw new InvalidQueryException("Column specified in query must be a string type");
    }
  }
}
