package org.databiosphere.workspacedataservice.dao;

import com.google.common.collect.Lists;
import org.databiosphere.workspacedataservice.service.model.DataTypeMapping;
import org.databiosphere.workspacedataservice.service.model.SingleTenantEntityReference;
import org.databiosphere.workspacedataservice.shared.model.Entity;
import org.databiosphere.workspacedataservice.shared.model.EntityAttributes;
import org.databiosphere.workspacedataservice.shared.model.EntityId;
import org.databiosphere.workspacedataservice.shared.model.EntityType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.databiosphere.workspacedataservice.dao.EntitySystemColumn.ENTITY_ID;

@Repository
public class SingleTenantDao {

    private static final int CHUNK_SIZE = 1_000;
    private final NamedParameterJdbcTemplate namedTemplate;

    public SingleTenantDao(NamedParameterJdbcTemplate namedTemplate) {
        this.namedTemplate = namedTemplate;
    }

    public boolean workspaceSchemaExists(UUID workspaceId){
        return namedTemplate.queryForObject("select exists(select 1 from information_schema.schemata WHERE schema_name = :workspaceSchema)",
                new MapSqlParameterSource("workspaceSchema", workspaceId.toString()), Boolean.class);
    }

    public void createSchema(UUID workspaceId){
        namedTemplate.getJdbcTemplate().update("create schema \"" + workspaceId.toString() +"\"");
    }

    public boolean entityTypeExists(UUID workspaceId, String entityType){
        return namedTemplate.queryForObject("select exists(select from pg_tables where schemaname = :workspaceId AND tablename  = :entityType)",
                new MapSqlParameterSource(Map.of("workspaceId", workspaceId.toString(), "entityType", entityType)), Boolean.class);
    }

    public void createEntityType(UUID workspaceId, Map<String, DataTypeMapping> tableInfo, String tableName, Set<SingleTenantEntityReference> referencedEntityTypes){
        String columnDefs = genColumnDefs(tableInfo);
        namedTemplate.getJdbcTemplate().update("create table " + getQualifiedTableName(tableName, workspaceId) + "( " + columnDefs + ")");
        for (SingleTenantEntityReference referencedEntityType : referencedEntityTypes) {
            addForeignKeyForReference(tableName, referencedEntityType.getReferencedEntityType().getName(), workspaceId, referencedEntityType.getReferenceColName());
        }
    }

    private String getQualifiedTableName(String entityType, UUID workspaceId){
        return "\"" + workspaceId.toString() + "\".\"" + entityType + "\"";
    }

    public Map<String, DataTypeMapping> getExistingTableSchema(UUID workspaceId, String tableName){
        MapSqlParameterSource params = new MapSqlParameterSource("workspaceId", workspaceId.toString());
        params.addValue("tableName", tableName);
        return namedTemplate.query("select column_name, data_type from INFORMATION_SCHEMA.COLUMNS where table_schema = :workspaceId " +
                "and table_name = :tableName", params, rs -> {
            Map<String, DataTypeMapping> result = new HashMap<>();
            while(rs.next()){
                result.put(rs.getString("column_name"), DataTypeMapping.fromPostgresType(rs.getString("data_type")));
            }
            return result;
        });
    }


    public void addColumn(UUID workspaceId, String tableName, String columnName, DataTypeMapping colType){
        namedTemplate.getJdbcTemplate().update("alter table "+ getQualifiedTableName(tableName, workspaceId) + " add column \"" + columnName + "\" " + colType.getPostgresType());
    }

    public void changeColumn(UUID workspaceId, String tableName, String columnName, DataTypeMapping newColType){
        namedTemplate.getJdbcTemplate().update("alter table " + getQualifiedTableName(tableName, workspaceId) + " alter column \"" + columnName + "\" TYPE " + newColType.getPostgresType());
    }

    private String genColumnDefs(Map<String, DataTypeMapping> tableInfo) {
        return ENTITY_ID.getColumnName() + " text primary key, "
                + tableInfo.entrySet().stream().map(e -> "\"" + e.getKey() + "\" " + e.getValue().getPostgresType()).collect(Collectors.joining(", "));
    }

    public void batchUpsert(UUID workspaceId, String entityType, List<Entity> entities, LinkedHashMap<String, DataTypeMapping> schema){
        schema.put(ENTITY_ID.getColumnName(), DataTypeMapping.STRING);
        namedTemplate.getJdbcTemplate().batchUpdate(genInsertStatement(workspaceId, entityType, schema),
                getInsertBatchArgs(entities, schema.keySet()));
    }

    public void addForeignKeyForReference(String entityType, String referencedEntityType, UUID workspaceId, String referenceColName){
        String addFk = "alter table " + getQualifiedTableName(entityType, workspaceId) + " add foreign key (\"" + referenceColName + "\") " +
                "references " + getQualifiedTableName(referencedEntityType, workspaceId);
        namedTemplate.getJdbcTemplate().execute(addFk);
    }

    public Set<String> getReferenceCols(UUID workspaceId, String tableName){
        return new HashSet<>(namedTemplate.queryForList("SELECT kcu.column_name FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage kcu " +
                "ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema " +
                "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema " +
                "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = :workspace AND tc.table_name= :tableName",
                Map.of("workspace", workspaceId.toString(), "tableName", tableName), String.class));
    }


    private String genColUpsertUpdates(Set<String> cols) {
        return cols.stream().filter(c -> !ENTITY_ID.getColumnName().equals(c)).map(c -> "\"" + c + "\"" + " = excluded.\"" + c + "\"").collect(Collectors.joining(", "));
    }

    private List<Object[]> getInsertBatchArgs(List<Entity> entities, Set<String> colNames) {
        List<Object[]> result = new ArrayList<>();
        for (Entity entity : entities) {
            Object[] row = new Object[colNames.size()];
            int i = 0;
            for (String col : colNames) {
                if(col.equals(ENTITY_ID.getColumnName())){
                    row[i++] = entity.getName().getEntityIdentifier();
                } else {
                    row[i++] = entity.getAttributes().getAttributes().get(col);
                }
            }
            result.add(row);
        }
        return result;
    }

    private String genInsertStatement(UUID workspaceId, String entityType, Map<String, DataTypeMapping> schema) {
        return "insert into " + getQualifiedTableName(entityType, workspaceId) + "(" +
                getInsertColList(schema.keySet()) + ") values (" + getInsertParamList(schema.values()) +") " +
                "on conflict (" + ENTITY_ID.getColumnName() +") do update set " + genColUpsertUpdates(schema.keySet());
    }

    private String getInsertParamList(Collection<DataTypeMapping> existingTableSchema) {
        return existingTableSchema.stream().map(m -> m.getPostgresType().equalsIgnoreCase("jsonb") ? "? :: jsonb" : "?").collect(Collectors.joining(", "));
    }


    private String getInsertColList(Set<String> existingTableSchema) {
        return existingTableSchema.stream().map(col ->"\"" + col + "\"").collect(Collectors.joining(", "));
    }

    public int getEntityCount(String entityType, UUID workspaceId) {
        return namedTemplate.getJdbcTemplate().queryForObject("select count(*) from " + getQualifiedTableName(entityType, workspaceId), Integer.class);
    }

    public int getFilteredEntityCount(UUID workspaceId, String entityType, String filterTerms, Map<String, DataTypeMapping> schema) {
        return namedTemplate.queryForObject("select count(*) from " + getQualifiedTableName(entityType, workspaceId)
                        + " where " + buildFilterSql(schema.keySet()),
                new MapSqlParameterSource("filterTerms", "%"+filterTerms+"%"), Integer.class);
    }

    private String buildFilterSql(Set<String> cols) {
        return cols.stream().map(c -> c + " ilike :filterTerms").collect(Collectors.joining(" OR "));
    }

    public int getEntityCount(String entityTypeName, List<String> entityNamesToDelete, UUID workspaceId) {
        List<List<String>> chunks = Lists.partition(entityNamesToDelete, CHUNK_SIZE);
        int result = 0;
        for (List<String> chunk : chunks) {
            result += namedTemplate.queryForObject("select count(*) from " + getQualifiedTableName(entityTypeName, workspaceId)
                            + " where " + ENTITY_ID.getColumnName() + " in (:entities)",
                    new MapSqlParameterSource("entities", chunk), Integer.class);
        }
        return result;
    }

    public void deleteEntities(String entityTypeName, List<String> entityNamesToDelete, UUID workspaceId){
        List<List<String>> chunks = Lists.partition(entityNamesToDelete, CHUNK_SIZE);
        for (List<String> chunk : chunks) {
            namedTemplate.update("delete from " + getQualifiedTableName(entityTypeName, workspaceId) + " where " + ENTITY_ID.getColumnName() + " in (:entities)",
                    new MapSqlParameterSource("entities", chunk));
        }
    }


    private class EntityRowMapper implements RowMapper<Entity> {
        private final String entityType;

        private EntityRowMapper(String entityType) {
            this.entityType = entityType;
        }

        @Override
        public Entity mapRow(ResultSet rs, int rowNum) throws SQLException {
            Entity entity = new Entity(new EntityId(rs.getString(ENTITY_ID.getColumnName())), new EntityType(entityType), new EntityAttributes(getAttributes(rs)));
            entity.setDeleted(false);
            return entity;
        }

        private Map<String, Object> getAttributes(ResultSet rs) {
            try {
                ResultSetMetaData metaData = rs.getMetaData();
                Map<String, Object> attributes = new HashMap<>();
                Set<String> systemCols = Arrays.stream(EntitySystemColumn.values()).map(EntitySystemColumn::getColumnName).collect(Collectors.toSet());
                for (int j = 0; j < metaData.getColumnCount(); j++) {
                    String columnName = metaData.getColumnName(j+1);
                    if (systemCols.contains(columnName)) {
                        continue;
                    }
                    attributes.put(columnName, rs.getObject(columnName));
                }
                return attributes;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public List<Entity> getSelectedEntities(String entityType, int pageSize, int i, String filterTerms, String sortField,
                                            String sortDirection, List<String> fields, UUID workspaceId, Map<String, DataTypeMapping> schema) {
        if(filterTerms.isBlank()){
            return namedTemplate.getJdbcTemplate().query("select " + getFieldList(fields) + " from "
                    + getQualifiedTableName(entityType, workspaceId) + " order by " + sortField
                    + " " + sortDirection + " limit " + pageSize + " offset " + i, new EntityRowMapper(entityType));
        } else {
            return namedTemplate.query("select " + getFieldList(fields) + " from "
                    + getQualifiedTableName(entityType, workspaceId) + " where " + buildFilterSql(schema.keySet()) + " order by " + sortField
                    + " " + sortDirection + " limit " + pageSize + " offset " + i, new MapSqlParameterSource("filterTerms", "%"+filterTerms+"%"),
                    new EntityRowMapper(entityType));
        }

    }

    private String getFieldList(List<String> fields) {
        return (fields == null || fields.isEmpty()) ? "*" :
                Stream.concat(fields.stream(), Stream.of(ENTITY_ID.getColumnName())).collect(Collectors.joining(", "));
    }
}
