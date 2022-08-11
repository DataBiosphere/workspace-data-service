package org.databiosphere.workspacedataservice.controller;

import com.google.common.base.Preconditions;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.databiosphere.workspacedataservice.dao.EntityDao;
import org.databiosphere.workspacedataservice.service.DataTypeInferer;
import org.databiosphere.workspacedataservice.service.RefUtils;
import org.databiosphere.workspacedataservice.service.model.*;
import org.databiosphere.workspacedataservice.shared.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class EntityController {

    private final EntityDao entityDao;
    private DataTypeInferer inferer;

    public EntityController(EntityDao entityDao) {
        this.entityDao = entityDao;
        this.inferer = new DataTypeInferer();
    }

    @PatchMapping("/{instanceId}/entities/{version}/{entityType}/{entityId}")
    public ResponseEntity<EntityResponse> updateSingleEntity(@PathVariable("instanceId") UUID instanceId,
                                                             @PathVariable("version") String version,
                                                             @PathVariable("entityType") EntityType entityType,
                                                             @PathVariable("entityId") EntityId entityId,
                                                             @RequestBody EntityRequest entityRequest){
        Preconditions.checkArgument(version.equals("v0.2"));
        String entityTypeName = entityType.getName();
        Entity singleEntity = entityDao.getSingleEntity(instanceId, entityType, entityId,
                entityDao.getReferenceCols(instanceId, entityTypeName));
        if(singleEntity == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        validateAttributes(instanceId, entityTypeName, entityRequest.entityAttributes());
        Map<String, Object> updatedAtts = entityRequest.entityAttributes().getAttributes();
        Map<String, Object> allAttrs = new HashMap<>(singleEntity.getAttributes().getAttributes());
        allAttrs.putAll(updatedAtts);

        Map<String, DataTypeMapping> typeMapping = inferer.inferTypes(updatedAtts);
        //TODO: remove entityType/entityName JSON object format for references and move to URIs in the request/response payloads
        Map<String, DataTypeMapping> existingTableSchema = entityDao.getExistingTableSchema(instanceId, entityTypeName);
        singleEntity.setAttributes(new EntityAttributes(allAttrs));
        List<Entity> entities = Collections.singletonList(singleEntity);
        Map<String, DataTypeMapping> updatedSchema = addOrUpdateColumnIfNeeded(instanceId, entityType.getName(), typeMapping, existingTableSchema, entities);
        entityDao.batchUpsert(instanceId, entityTypeName, entities, new LinkedHashMap<>(updatedSchema));
        EntityResponse response = new EntityResponse(entityId, entityType, singleEntity.getAttributes(), new EntityMetadata("TODO: SUPERFRESH"));
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private void validateAttributes(UUID instanceId, String entityTypeName, EntityAttributes attributes){
        List<SingleTenantEntityReference> refs = entityDao.getReferenceCols(instanceId, entityTypeName);
        for (Map.Entry<String, Object> entry : attributes.getAttributes().entrySet()){
            if (RefUtils.isReferenceValue(entry.getValue())){
                if (entityDao.getSingleEntity(instanceId, new EntityType(RefUtils.getTypeValue(entry.getValue())), new EntityId(RefUtils.getRefValue(entry.getValue())), refs) == null){
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Referenced entity %s not found".formatted(RefUtils.getTypeValue(entry.getValue())));
                }
            }
        }
    }

    private Map<String, DataTypeMapping> addOrUpdateColumnIfNeeded(UUID workspaceId, String entityType, Map<String, DataTypeMapping> schema, Map<String,
            DataTypeMapping> existingTableSchema, List<Entity> entities) {
        MapDifference<String, DataTypeMapping> difference = Maps.difference(existingTableSchema, schema);
        Map<String, DataTypeMapping> colsToAdd = difference.entriesOnlyOnRight();
        Set<SingleTenantEntityReference> references = RefUtils.findEntityReferences(entities);
        Map<String, List<SingleTenantEntityReference>> newRefCols = references.stream().collect(Collectors.groupingBy(SingleTenantEntityReference::getReferenceColName));
        //TODO: better communicate to the user that they're trying to assign multiple entity types to a single column
        Preconditions.checkArgument(newRefCols.values().stream().filter(l -> l.size() > 1).findAny().isEmpty());
        for (String col : colsToAdd.keySet()) {
            entityDao.addColumn(workspaceId, entityType, col, colsToAdd.get(col));
            schema.put(col, colsToAdd.get(col));
            if(newRefCols.containsKey(col)) {
                String referencedEntityType = null;
                try {
                    referencedEntityType = newRefCols.get(col).get(0).getReferencedEntityType().getName();
                    entityDao.addForeignKeyForReference(entityType, referencedEntityType, workspaceId, col);
                } catch (MissingReferencedTableException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "It looks like you're attempting to assign a reference " +
                            "to a table, " + referencedEntityType + ", that does not exist");
                }
            }
        }
        if(!entityDao.getReferenceCols(workspaceId, entityType).stream().map(SingleTenantEntityReference::getReferenceColName)
                .collect(Collectors.toSet()).containsAll(newRefCols.keySet())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "It looks like you're attempting to assign a reference " +
                    "to an existing column that was not configured for references");
        }
        Map<String, MapDifference.ValueDifference<DataTypeMapping>> differenceMap = difference.entriesDiffering();
        for (String column : differenceMap.keySet()) {
            MapDifference.ValueDifference<DataTypeMapping> valueDifference = differenceMap.get(column);
            DataTypeMapping updatedColType = inferer.selectBestType(valueDifference.leftValue(), valueDifference.rightValue());
            entityDao.changeColumn(workspaceId, entityType, column, updatedColType);
            schema.put(column, updatedColType);
        }
        return schema;
    }


    @GetMapping("/{instanceId}/entities/{version}/{entityType}/{entityId}")
    public ResponseEntity<EntityResponse> getSingleEntity(@PathVariable("instanceId") UUID instanceId,
                                              @PathVariable("version") String version,
                                              @PathVariable("entityType") EntityType entityType,
                                              @PathVariable("entityId") EntityId entityId) {
        Preconditions.checkArgument(version.equals("v0.2"));
        Entity result = entityDao.getSingleEntity(instanceId, entityType, entityId, entityDao.getReferenceCols(instanceId, entityType.getName()));
        if (result == null){
            //TODO: standard exception classes
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }
        EntityResponse response = new EntityResponse(entityId, entityType, result.getAttributes(),
                new EntityMetadata("TODO: ENTITYMETADATA"));
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/{instanceId}/entities/{version}/{entityType}/{entityId}")
    public ResponseEntity<EntityResponse> putSingleEntity(@PathVariable("instanceId") UUID instanceId,
                                                             @PathVariable("version") String version,
                                                             @PathVariable("entityType") EntityType entityType,
                                                             @PathVariable("entityId") EntityId entityId,
                                                             @RequestBody EntityRequest entityRequest) {
        Preconditions.checkArgument(version.equals("v0.2"));
        String entityTypeName = entityType.getName();
        Map<String, DataTypeMapping> schema = inferer.inferTypes(entityRequest.entityAttributes().getAttributes());
        //make sure entitytype exists
        if (!entityDao.entityTypeExists(instanceId, entityTypeName)){
            try{
                Set<SingleTenantEntityReference> references = RefUtils.findEntityReferences(Collections.singletonList(new Entity(entityId, entityType, entityRequest.entityAttributes())));
                entityDao.createEntityType(instanceId, schema, entityTypeName, references);
            } catch (MissingReferencedTableException e){
                return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }
        validateAttributes(instanceId, entityTypeName, entityRequest.entityAttributes());
        Entity singleEntity = entityDao.getSingleEntity(instanceId, entityType, entityId,
                entityDao.getReferenceCols(instanceId, entityTypeName));
        if(singleEntity == null){
            //create new entity
            entityDao.createSingleEntity(instanceId, entityTypeName, new Entity(entityId, entityType, entityRequest.entityAttributes()), new LinkedHashMap<>(schema));
            EntityResponse response = new EntityResponse(entityId, entityType, entityRequest.entityAttributes(), new EntityMetadata("TODO"));
            return new ResponseEntity(response, HttpStatus.CREATED);
        } else {
            if (compareEntities(singleEntity, entityRequest)){
                //Nothing to update
                EntityResponse response = new EntityResponse(entityId, entityType, singleEntity.getAttributes(),
                        new EntityMetadata("TODO"));
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            //add any columns that need adding
            Map<String, DataTypeMapping> existingTableSchema = entityDao.getExistingTableSchema(instanceId, entityTypeName);
            Map<String, DataTypeMapping> typeMapping = inferer.inferTypes(entityRequest.entityAttributes().getAttributes());
            addOrUpdateColumnIfNeeded(instanceId, entityType.getName(), typeMapping, existingTableSchema, Collections.singletonList(new Entity(entityRequest)));
            //remove all attribute values and put new attribute values
             @PutMapping("/{instanceId}/entities/{version}/{entityType}/{entityId}")
    public ResponseEntity<EntityResponse> putSingleEntity(@PathVariable("instanceId") UUID instanceId,
                                                             @PathVariable("version") String version,
                                                             @PathVariable("entityType") EntityType entityType,
                                                             @PathVariable("entityId") EntityId entityId,
                                                             @RequestBody EntityRequest entityRequest) {
        Preconditions.checkArgument(version.equals("v0.2"));
        String entityTypeName = entityType.getName();
        Map<String, Object> attributesInRequest = entityRequest.entityAttributes().getAttributes();
        Map<String, DataTypeMapping> requestSchema = inferer.inferTypes(attributesInRequest);
        if(!entityDao.entityTypeExists(instanceId, entityTypeName)){
            createEntityTypeAndInsertEntities(instanceId, entityRequest, entityTypeName, requestSchema);
        } else {
            Map<String, DataTypeMapping> existingTableSchema = entityDao.getExistingTableSchema(instanceId, entityTypeName);
            //null out any attributes that already exist but aren't in the request
            existingTableSchema.keySet().forEach(attr -> attributesInRequest.putIfAbsent(attr, null));
            Entity entity = new Entity(entityId, entityType, entityRequest.entityAttributes());
            List<Entity> entities = Collections.singletonList(entity);
            addOrUpdateColumnIfNeeded(instanceId, entityType.getName(), requestSchema, existingTableSchema, entities);
            LinkedHashMap<String, DataTypeMapping> combinedSchema = new LinkedHashMap<>(requestSchema);
            combinedSchema.putAll(existingTableSchema);
            entityDao.batchUpsert(instanceId, entityTypeName, entities, combinedSchema);
        }
        EntityResponse response = new EntityResponse(entityId, entityType, entityRequest.entityAttributes(), new EntityMetadata("TODO"));
        return new ResponseEntity(response, HttpStatus.CREATED);
    }

    private void createEntityTypeAndInsertEntities(UUID instanceId, EntityRequest entityRequest, String entityTypeName, Map<String, DataTypeMapping> requestSchema) {
        try {
            List<Entity> entities = Collections.singletonList(new Entity(entityRequest.entityId(), entityRequest.entityType(), entityRequest.entityAttributes()));
            entityDao.createEntityType(instanceId, requestSchema, entityTypeName,
                    RefUtils.findEntityReferences(entities));
            entityDao.batchUpsert(instanceId, entityTypeName, entities, new LinkedHashMap<>(requestSchema));
        } catch (MissingReferencedTableException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "It looks like you're attempting to assign a reference " +
                    "to a table that does not exist", e);
        }
    }
            //TODO: Should we get the entity to return or use submitted attributes (as is done in Patch and above)
            Entity updatedEntity = entityDao.getSingleEntity(instanceId, entityType, entityId, entityDao.getReferenceCols(instanceId, entityType.getName()));
            EntityResponse response = new EntityResponse(entityId, entityType, updatedEntity.getAttributes(),
                    new EntityMetadata("TODO"));
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    //todo: compare deleted as well?
    private boolean compareEntities(Entity oldEntity, EntityRequest newEntity){
        return oldEntity.getAttributes().equals(newEntity.entityAttributes());
    }


}
