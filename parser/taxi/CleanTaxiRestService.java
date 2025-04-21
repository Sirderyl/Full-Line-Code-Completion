package uk.ac.newcastle.enterprisemiddleware.taxi;

import uk.ac.newcastle.enterprisemiddleware.util.RestServiceException;

import javax.persistence.NoResultException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CleanTaxiRestService {

    Logger log;

    TaxiService taxiService;

    public Response getTaxiById(Long id) {
        Taxi taxi = taxiService.getTaxiById(id);
        if (taxi == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        log.info("getTaxiById " + id + ": found Taxi = " + taxi);
        return Response.ok(taxi).build();
    }

    public Response getTaxiByRegistration(String registration) {
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

    public Response getAllTaxis() {
        return Response.ok(taxiService.getAllTaxis()).build();
    }

    public Response createTaxi(TaxiDTO taxiDTO) {
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

    public Response updateTaxi(Long id, TaxiDTO updatedTaxiDTO) {
        if (updatedTaxiDTO == null) {
            throw new RestServiceException("Invalid Taxi supplied", Response.Status.BAD_REQUEST);
        }
        Taxi existingTaxi = taxiService.getTaxiById(id);
        if (existingTaxi == null) {
            throw new RestServiceException("Taxi with id " + id + " not found", Response.Status.NOT_FOUND);
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

    public Response deleteTaxi(Long id) {
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
