package uk.ac.newcastle.enterprisemiddleware.taxi;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.Cache;
import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Path("/taxis")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TaxiRestService {

    @Inject
    @Named("logger")
    Logger log;

    @Inject
    TaxiService taxiService;


    /**
     * Endpoint to retrieve a taxi by ID.
     * @param id the ID of the taxi to retrieve
     * @return a response containing the taxi or 404 if not found.
     */
    @GET
    @Cache
    @Path("/{id:[0-9]+}")
    @Operation(
            summary = "Fetch a Taxi by ID",
            description = "Returns a JSON representation of the Taxi object with the provided ID."
    )
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Taxi found"),
            @APIResponse(responseCode = "404", description = "Taxi with ID not found.")
    })
    public Response getTaxiById(
            @Parameter(description = "ID of Taxi to be fetched")
            @Schema(minimum = "0", required = true)
            @PathParam("id")
            Long id) {

        Taxi taxi = taxiService.getTaxiById(id);
        if (taxi == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        log.info("getTaxiById " + id + ": found Taxi = " + taxi);

        return Response.ok(taxi).build();
    }

    /**
     * Endpoint to retrieve a taxi by registration.
     * @param registration the registration of the taxi to retrieve
     * @return a response containing the taxi or 404 if not found.
     */
    @GET
    @Cache
    @Path("/registration/{registration}")
    @Operation(
            summary = "Fetch a Taxi by Registration",
            description = "Returns a JSON representation of the Taxi object with the provided registration."
    )
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description ="Taxi found"),
            @APIResponse(responseCode = "404", description = "Taxi with registration not found")
    })
    public Response getTaxiByRegistration(
            @Parameter(description = "Registration of Taxi to be fetched", required = true)
            @PathParam("registration")
            String registration) {

        Taxi taxi;

        if (registration.length() != 7) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            taxi = taxiService.getTaxiByRegistration(registration);
        } catch (NoResultException e) {
            throw new RestServiceException("No taxi with registration found: " + registration, Response.Status.NOT_FOUND);
        }

        return Response.ok(taxi).build();
    }

    /**
     * Endpoint to retrieve a list of all taxis.
     * @return a response containing the list of taxis
     */
    @GET
    @Operation(summary = "Fetch all Taxis", description = "Returns a JSON array of all stored Taxi objects.")
    public Response getAllTaxis() {
        return Response.ok(taxiService.getAllTaxis()).build();
    }

    /**
     * Endpoint to create a new Taxi.
     * @param taxiDTO the taxi to create
     * @return a response with the created taxi
     */
    @POST
    @Operation(summary = "Create a new Taxi", description = "Add a new Taxi to the database")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Taxi created successfully."),
            @APIResponse(responseCode = "400", description = "Invalid Taxi supplied in request body"),
            @APIResponse(responseCode = "409", description = "Taxi supplied in request body conflicts with an existing Taxi"),
            @APIResponse(responseCode = "500", description = "An unexpected error occurred whilst processing the request")
    })
    @Transactional
    public Response createTaxi(
            @Parameter(description = "JSON representation of Taxi object to be added to the database", required = true)
            TaxiDTO taxiDTO) {

        if (taxiDTO == null) {
            throw new RestServiceException("Bad Request", Response.Status.BAD_REQUEST);
        }

        if (taxiDTO.getNumberOfSeats() < 2 || taxiDTO.getNumberOfSeats() > 20) {
            throw new RestServiceException("Number of seats must be between 2 and 20", Response.Status.BAD_REQUEST);
        }

        Taxi taxi = new Taxi();
        taxi.setId(null);
        taxi.setRegistration(taxiDTO.getRegistration());
        taxi.setNumberOfSeats(taxiDTO.getNumberOfSeats());

        Response.ResponseBuilder builder;

        try {
            taxiService.createTaxi(taxi);
            builder = Response.status(Response.Status.CREATED).entity(taxi);
        } catch (ConstraintViolationException e) {
            Map<String, String> responseObj = new HashMap<>();

            for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
                responseObj.put(violation.getPropertyPath().toString(), violation.getMessage());
            }

            throw new RestServiceException("Bad Request", responseObj, Response.Status.BAD_REQUEST, e);

        } catch (UniqueRegistrationException e) {
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("registration", "This registration already exists. It must be unique");
            throw new RestServiceException("Taxi details already in use", responseObj, Response.Status.CONFLICT, e);

        } catch (Exception e) {
            throw new RestServiceException(e);
        }

        log.info("createTaxi completed. Taxi = " + taxi);
        return builder.build();
    }

    /**
     * Endpoint to update an existing Taxi record.
     * @param id the ID of the taxi to update
     * @param updatedTaxiDTO the updated Taxi information
     * @return a response with status 200 if updated, other statuses in case of issues
     */
    @PUT
    @Path("/{id:[0-9]+}")
    @Operation(summary = "Update a Taxi record", description = "Update existing Taxi record in the database")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Taxi updated successfully"),
            @APIResponse(responseCode = "400", description = "Invalid Taxi supplied in request body"),
            @APIResponse(responseCode = "404", description = "Taxi with id not found"),
            @APIResponse(responseCode = "409", description = "Taxi details supplied in request body conflict with another existing Taxi"),
            @APIResponse(responseCode = "500", description = "An unexpected error occurred whilst processing the request")
    })
    @Transactional
    public Response updateTaxi(
            @Parameter(description = "ID of Taxi to be updated", required = true)
            @Schema(minimum = "0")
            @PathParam("id")
            Long id,
            @Parameter(description = "JSON representation of Taxi object to be updated", required = true)
            @Valid
            TaxiDTO updatedTaxiDTO) {

        if (updatedTaxiDTO == null) {
            throw new RestServiceException("Invalid Taxi supplied", Response.Status.BAD_REQUEST);
        }

        Taxi existingTaxi = taxiService.getTaxiById(id);

        if (existingTaxi == null) {
            throw new RestServiceException("Taxi with id " + id + " not found",
                    Response.Status.NOT_FOUND);
        }

        Response.ResponseBuilder builder;

        try {
            existingTaxi.setRegistration(updatedTaxiDTO.getRegistration());
            existingTaxi.setNumberOfSeats(updatedTaxiDTO.getNumberOfSeats());

            taxiService.updateTaxi(existingTaxi);

            builder = Response.ok(existingTaxi);
        } catch (ConstraintViolationException e) {
            Map<String, String> responseObj = new HashMap<>();

            for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
                responseObj.put(violation.getPropertyPath().toString(), violation.getMessage());
            }

            throw new RestServiceException("Bad Request", responseObj, Response.Status.BAD_REQUEST, e);

        } catch (UniqueRegistrationException e) {
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("registration", "This registration already exists. It must be unique");
            throw new RestServiceException("Taxi details already in use", responseObj, Response.Status.BAD_REQUEST, e);

        } catch (Exception e) {
            throw new RestServiceException(e);
        }

        log.info("updateTaxi completed. Taxi = " + updatedTaxiDTO);

        return builder.build();
    }

    /**
     * Endpoint to delete an existing Taxi from the database.
     * @param id the ID of the taxi to delete
     * @return a response with status 204 if deleted, other statuses in case of issues
     */
    @DELETE
    @Path("/{id:[0-9]+}")
    @Operation(summary = "Delete an existing Taxi record", description = "Deletes a Taxi from the database")
    @APIResponses(value = {
            @APIResponse(responseCode = "204", description = "The taxi has been successfully deleted"),
            @APIResponse(responseCode = "400", description = "Invalid Taxi ID supplied"),
            @APIResponse(responseCode = "404", description = "Taxi with ID not found"),
            @APIResponse(responseCode = "500", description = "An unexpected error occurred whilst processing the request")
    })
    @Transactional
    public Response deleteTaxi(
            @Parameter(description = "ID of Taxi to be deleted", required = true)
            @Schema(minimum = "0")
            @PathParam("id")
            Long id) {

        Response.ResponseBuilder builder;

        Taxi taxi = taxiService.getTaxiById(id);

        if (taxi == null) {
            throw new RestServiceException("Taxi with id " + id + " not found", Response.Status.NOT_FOUND);
        }

        try {
            taxiService.deleteTaxi(taxi);
            builder = Response.noContent();
        } catch (Exception e) {
            throw new RestServiceException(e);
        }

        log.info("deleteTaxi completed. Taxi = " + taxi);

        return builder.build();
    }
}
