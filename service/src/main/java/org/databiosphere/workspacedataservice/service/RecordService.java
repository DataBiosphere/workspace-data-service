package org.databiosphere.workspacedataservice.service;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.databiosphere.workspacedataservice.dao.RecordDao;
import org.databiosphere.workspacedataservice.service.model.DataTypeMapping;
import org.databiosphere.workspacedataservice.service.model.Relation;
import org.databiosphere.workspacedataservice.service.model.RelationValue;
import org.databiosphere.workspacedataservice.service.model.exception.BatchWriteException;
import org.databiosphere.workspacedataservice.service.model.exception.InvalidRelationException;
import org.databiosphere.workspacedataservice.shared.model.Record;
import org.databiosphere.workspacedataservice.shared.model.RecordAttributes;
import org.databiosphere.workspacedataservice.shared.model.RecordType;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static org.databiosphere.workspacedataservice.service.model.ReservedNames.RECORD_ID;

@Service
public class RecordService {

    private final RecordDao recordDao;

    private final DataTypeInferer inferer;

    public RecordService(RecordDao recordDao, DataTypeInferer inferer) {
        this.recordDao = recordDao;
        this.inferer = inferer;
    }

    public void prepareAndUpsert(UUID instanceId, RecordType recordType, List<Record> records,
                                 Map<String, DataTypeMapping> requestSchema) {
        prepareAndUpsert(instanceId, recordType, records, requestSchema, RECORD_ID);
    }


    @Transactional
    public void prepareAndUpsert(UUID instanceId, RecordType recordType, List<Record> records,
                             Map<String, DataTypeMapping> requestSchema, String primaryKey) {
        //Identify relation arrays
        Map<String, DataTypeMapping> relationArrays = requestSchema.entrySet().stream()
                .filter(entry -> entry.getValue() == DataTypeMapping.ARRAY_OF_RELATION)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Relation, List<RelationValue>> relationArrayValues = getAllRelationArrayValues(records, relationArrays);
        recordDao.batchUpsert(instanceId, recordType, records, requestSchema, primaryKey);
        for (Map.Entry<Relation, List<RelationValue>> rel : relationArrayValues.entrySet()) {
            recordDao.insertIntoJoin(instanceId, rel.getKey(), recordType, rel.getValue());
        }
    }

    private Map<Relation, List<RelationValue>> getAllRelationArrayValues(List<Record> records, Map<String, DataTypeMapping> relationArrays){
        Map<Relation, List<RelationValue>> relationArrayValues = new HashMap<>();
        for (Record rec : records) {
            for (Map.Entry<String, Object> attribute : rec.attributeSet()){
                if (relationArrays.containsKey(attribute.getKey()) && attribute.getValue() != null){
                    //How to read relation list depends on its source, which we don't know here so we have to check
                    List<String> rels;
                    if (attribute.getValue() instanceof List<?>){
                        rels = (List<String>) attribute.getValue();
                    } else {
                        rels = Arrays.asList(inferer.getArrayOfType(attribute.getValue().toString(), String[].class));
                    }
                    Relation relDef = new Relation(attribute.getKey(), RelationUtils.getTypeValueForList(rels));
                    List<RelationValue> relList = relationArrayValues.getOrDefault(relDef, new ArrayList<>());
                    relList.addAll(rels.stream().map(r -> getRelVal(rec, r)).toList());
                    relationArrayValues.put(relDef, relList);
                }
            }
        }
        return relationArrayValues;
    }

    private RelationValue getRelVal(Record fromRecord, String toString){
        return new RelationValue(fromRecord, new Record(RelationUtils.getRelationValue(toString), RelationUtils.getTypeValue(toString), new RecordAttributes(Collections.emptyMap())));
    }

    public void batchUpsertWithErrorCapture(UUID instanceId, RecordType recordType, List<Record> records,
                                            Map<String, DataTypeMapping> schema, String primaryKey) {
        try {
            prepareAndUpsert(instanceId, recordType, records, schema, primaryKey);
        } catch (DataAccessException e) {
            if (isDataMismatchException(e)) {
                Map<String, DataTypeMapping> recordTypeSchemaWithoutId = new HashMap<>(schema);
                recordTypeSchemaWithoutId.remove(primaryKey);
                List<String> rowErrors = checkEachRow(records, recordTypeSchemaWithoutId);
                if (!rowErrors.isEmpty()) {
                    throw new BatchWriteException(rowErrors);
                }
            }
            throw e;
        }
    }

    private List<String> checkEachRow(List<Record> records, Map<String, DataTypeMapping> recordTypeSchema) {
        List<String> result = new ArrayList<>();
        for (Record rcd : records) {
            Map<String, DataTypeMapping> schemaForRecord = inferer.inferTypes(rcd.getAttributes(),
                    InBoundDataSource.JSON);
            if (!schemaForRecord.equals(recordTypeSchema)) {
                MapDifference<String, DataTypeMapping> difference = Maps.difference(schemaForRecord, recordTypeSchema);
                Map<String, MapDifference.ValueDifference<DataTypeMapping>> differenceMap = difference
                        .entriesDiffering();
                result.add(convertSchemaDiffToErrorMessage(differenceMap, rcd));
            }
        }
        return result;
    }

    private String convertSchemaDiffToErrorMessage(
            Map<String, MapDifference.ValueDifference<DataTypeMapping>> differenceMap, Record rcd) {
        return differenceMap.keySet().stream()
                .map(attr -> rcd.getId() + "." + attr + " is a " + differenceMap.get(attr).leftValue()
                        + " in the request but is defined as " + differenceMap.get(attr).rightValue()
                        + " in the record type definition for " + rcd.getRecordType())
                .collect(Collectors.joining("\n"));
    }

    private boolean isDataMismatchException(DataAccessException e) {
        return e.getRootCause()instanceof SQLException sqlException && sqlException.getSQLState().equals("42804");
    }
}
