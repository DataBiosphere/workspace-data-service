/**
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech) (7.8.0).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
package org.databiosphere.workspacedataservice.generated;

import org.databiosphere.workspacedataservice.generated.CapabilitiesServerModel;
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
@Tag(name = "Capabilities", description = "Describes the features of WDS")
public interface CapabilitiesApi {

    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    /**
     * GET /capabilities/v1 : Describes the capabilities of this WDS version.
     *
     * @return key-value pairs describing capabilities (status code 200)
     */
    @Operation(
        operationId = "capabilities",
        summary = "Describes the capabilities of this WDS version.",
        tags = { "Capabilities" },
        responses = {
            @ApiResponse(responseCode = "200", description = "key-value pairs describing capabilities", content = {
                @Content(mediaType = "application/json", schema = @Schema(implementation = CapabilitiesServerModel.class))
            })
        }
    )
    @RequestMapping(
        method = RequestMethod.GET,
        value = "/capabilities/v1",
        produces = { "application/json" }
    )
    
    default ResponseEntity<CapabilitiesServerModel> capabilities(
        
    ) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

}
