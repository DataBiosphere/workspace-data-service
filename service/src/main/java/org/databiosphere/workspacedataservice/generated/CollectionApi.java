/**
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech) (7.7.0).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
package org.databiosphere.workspacedataservice.generated;

import org.databiosphere.workspacedataservice.generated.CollectionServerModel;
import java.util.UUID;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.annotation.Generated;

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.7.0")
@Validated
@Tag(name = "Collection", description = "Collection APIs")
public interface CollectionApi {

    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    /**
     * POST /collections/v1/{workspaceId} : Create a collection in this workspace.
     * If collection id is specified in the request body, it must be a valid UUID. If omitted, the system will generate an id. 
     *
     * @param workspaceId Workspace id (required)
     * @param collectionServerModel The collection to create (required)
     * @return The collection just created. (status code 201)
     */
    @Operation(
        operationId = "createCollectionV1",
        summary = "Create a collection in this workspace.",
        description = "If collection id is specified in the request body, it must be a valid UUID. If omitted, the system will generate an id. ",
        tags = { "Collection" },
        responses = {
            @ApiResponse(responseCode = "201", description = "The collection just created.", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = CollectionServerModel.class))
            })
        },
        security = {
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @RequestMapping(
        method = RequestMethod.POST,
        value = "/collections/v1/{workspaceId}",
        produces = { "application/json" },
        consumes = { "application/json" }
    )
    
    default ResponseEntity<CollectionServerModel> createCollectionV1(
        @Parameter(name = "workspaceId", description = "Workspace id", required = true, in = ParameterIn.PATH) @PathVariable("workspaceId") UUID workspaceId,
        @Parameter(name = "CollectionServerModel", description = "The collection to create", required = true) @Valid @RequestBody CollectionServerModel collectionServerModel
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }


    /**
     * DELETE /collections/v1/{workspaceId}/{collectionId} : Delete the specified collection.
     *
     * @param workspaceId Workspace id (required)
     * @param collectionId Collection id (required)
     * @return Collection has been deleted. (status code 204)
     */
    @Operation(
        operationId = "deleteCollectionV1",
        summary = "Delete the specified collection.",
        tags = { "Collection" },
        responses = {
            @ApiResponse(responseCode = "204", description = "Collection has been deleted.")
        },
        security = {
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @RequestMapping(
        method = RequestMethod.DELETE,
        value = "/collections/v1/{workspaceId}/{collectionId}"
    )
    
    default ResponseEntity<Void> deleteCollectionV1(
        @Parameter(name = "workspaceId", description = "Workspace id", required = true, in = ParameterIn.PATH) @PathVariable("workspaceId") UUID workspaceId,
        @Parameter(name = "collectionId", description = "Collection id", required = true, in = ParameterIn.PATH) @PathVariable("collectionId") UUID collectionId
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }


    /**
     * GET /collections/v1/{workspaceId}/{collectionId} : Retrieve a single collection.
     *
     * @param workspaceId Workspace id (required)
     * @param collectionId Collection id (required)
     * @return The collection object. (status code 200)
     */
    @Operation(
        operationId = "getCollectionV1",
        summary = "Retrieve a single collection.",
        tags = { "Collection" },
        responses = {
            @ApiResponse(responseCode = "200", description = "The collection object.", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = CollectionServerModel.class))
            })
        },
        security = {
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @RequestMapping(
        method = RequestMethod.GET,
        value = "/collections/v1/{workspaceId}/{collectionId}",
        produces = { "application/json" }
    )
    
    default ResponseEntity<CollectionServerModel> getCollectionV1(
        @Parameter(name = "workspaceId", description = "Workspace id", required = true, in = ParameterIn.PATH) @PathVariable("workspaceId") UUID workspaceId,
        @Parameter(name = "collectionId", description = "Collection id", required = true, in = ParameterIn.PATH) @PathVariable("collectionId") UUID collectionId
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }


    /**
     * GET /collections/v1/{workspaceId} : List all collections in this workspace.
     *
     * @param workspaceId Workspace id (required)
     * @return List of collections in this workspace. (status code 200)
     */
    @Operation(
        operationId = "listCollectionsV1",
        summary = "List all collections in this workspace.",
        tags = { "Collection" },
        responses = {
            @ApiResponse(responseCode = "200", description = "List of collections in this workspace.", content = {
                @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CollectionServerModel.class)))
            })
        },
        security = {
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @RequestMapping(
        method = RequestMethod.GET,
        value = "/collections/v1/{workspaceId}",
        produces = { "application/json" }
    )
    
    default ResponseEntity<List<CollectionServerModel>> listCollectionsV1(
        @Parameter(name = "workspaceId", description = "Workspace id", required = true, in = ParameterIn.PATH) @PathVariable("workspaceId") UUID workspaceId
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }


    /**
     * PUT /collections/v1/{workspaceId}/{collectionId} : Update the specified collection.
     * Collection id is optional in the request body. If specified, it must match the collection id specified in the url. 
     *
     * @param workspaceId Workspace id (required)
     * @param collectionId Collection id (required)
     * @param collectionServerModel The collection to update (required)
     * @return The collection just updated. (status code 200)
     */
    @Operation(
        operationId = "updateCollectionV1",
        summary = "Update the specified collection.",
        description = "Collection id is optional in the request body. If specified, it must match the collection id specified in the url. ",
        tags = { "Collection" },
        responses = {
            @ApiResponse(responseCode = "200", description = "The collection just updated.", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = CollectionServerModel.class))
            })
        },
        security = {
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @RequestMapping(
        method = RequestMethod.PUT,
        value = "/collections/v1/{workspaceId}/{collectionId}",
        produces = { "application/json" },
        consumes = { "application/json" }
    )
    
    default ResponseEntity<CollectionServerModel> updateCollectionV1(
        @Parameter(name = "workspaceId", description = "Workspace id", required = true, in = ParameterIn.PATH) @PathVariable("workspaceId") UUID workspaceId,
        @Parameter(name = "collectionId", description = "Collection id", required = true, in = ParameterIn.PATH) @PathVariable("collectionId") UUID collectionId,
        @Parameter(name = "CollectionServerModel", description = "The collection to update", required = true) @Valid @RequestBody CollectionServerModel collectionServerModel
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

}
