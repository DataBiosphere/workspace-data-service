/**
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech) (7.8.0).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
package org.databiosphere.workspacedataservice.generated;

import org.databiosphere.workspacedataservice.generated.GenericJobServerModel;
import java.util.UUID;
import org.databiosphere.workspacedataservice.generated.WorkspaceInitServerModel;
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

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
@Validated
@Tag(name = "Workspace", description = "Workspace APIs")
public interface WorkspaceApi {

    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    /**
     * POST /workspaces/v1/{workspaceId} : Initialize WDS for a given workspace.
     * when the &#x60;clone&#x60; key is present in the request body, this API will clone all collections from sourceWorkspaceId into this workspace 
     *
     * @param workspaceId Workspace id (required)
     * @param workspaceInitServerModel Initialization parameters (required)
     * @return Initialization request accepted. (status code 202)
     */
    @Operation(
        operationId = "initWorkspaceV1",
        summary = "Initialize WDS for a given workspace.",
        description = "when the `clone` key is present in the request body, this API will clone all collections from sourceWorkspaceId into this workspace ",
        tags = { "Workspace" },
        responses = {
            @ApiResponse(responseCode = "202", description = "Initialization request accepted.", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = GenericJobServerModel.class))
            })
        },
        security = {
            @SecurityRequirement(name = "bearerAuth")
        }
    )
    @RequestMapping(
        method = RequestMethod.POST,
        value = "/workspaces/v1/{workspaceId}",
        produces = { "application/json" },
        consumes = { "application/json" }
    )
    
    default ResponseEntity<GenericJobServerModel> initWorkspaceV1(
        @Parameter(name = "workspaceId", description = "Workspace id", required = true, in = ParameterIn.PATH) @PathVariable("workspaceId") UUID workspaceId,
        @Parameter(name = "WorkspaceInitServerModel", description = "Initialization parameters", required = true) @Valid @RequestBody WorkspaceInitServerModel workspaceInitServerModel
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

}
